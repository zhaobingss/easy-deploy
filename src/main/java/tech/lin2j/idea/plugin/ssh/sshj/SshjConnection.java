package tech.lin2j.idea.plugin.ssh.sshj;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.Response;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;
import net.schmizz.sshj.xfer.TransferListener;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import tech.lin2j.idea.plugin.model.ConfigHelper;
import tech.lin2j.idea.plugin.ssh.CommandLog;
import tech.lin2j.idea.plugin.ssh.SshConnection;
import tech.lin2j.idea.plugin.ssh.SshServer;
import tech.lin2j.idea.plugin.ssh.SshStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * @author linjinjia
 * @date 2024/1/5 21:19
 */
public class SshjConnection implements SshConnection {

    private static final Logger log = Logger.getInstance(SshjConnection.class);

    private final SshServer server;
    private final Deque<SSHClient> clients;
    private final SSHClient sshClient;
    private final boolean scpTransferMode;
    private final SFTPClient sftpClient;
    private SCPFileTransfer scpFileTransfer;

    public SshjConnection(Deque<SSHClient> clients, SshServer server) throws IOException {
        this.server = server;
        this.clients = clients;
        this.sshClient = clients.getLast();
        this.scpTransferMode = ConfigHelper.isSCPTransferMode();

        if (scpTransferMode) {
            this.sftpClient = null;
            this.scpFileTransfer = sshClient.newSCPFileTransfer();
        } else {
            this.sftpClient = sshClient.newSFTPClient();
            this.sftpClient.getFileTransfer().setPreserveAttributes(false);
        }
    }

    public void setTransferListener(TransferListener transferListener) {
        if (transferListener == null) {
            return;
        }
        if (scpTransferMode) {
            scpFileTransfer.setTransferListener(transferListener);
            return;
        }
        sftpClient.getFileTransfer().setTransferListener(transferListener);
    }

    public SSHClient getSshClient() {
        return sshClient;
    }

    @Override
    public boolean isConnected() {
        return sshClient.isConnected();
    }

    @Override
    public void upload(String local, String dest) throws IOException {
        if (scpTransferMode) {
            scpUpload(local, dest);
            return;
        }
        log.debug("Upload [" + local + "] to remote [" + dest + "]");
        File localFile = new File(local);
        if (!localFile.exists() || !localFile.canRead()) {
            throw new FileNotFoundException("local file not found: " + local);
        }
        // Ensure remote destination directory exists
        try {
            sftpClient.stat(dest);
        } catch (SFTPException e) {
            if (e.getStatusCode() == Response.StatusCode.NO_SUCH_FILE) {
                log.debug("Remote directory does not exist, creating: " + dest);
                mkdirs(dest);
            } else {
                // Permission denied or other real errors — don't swallow
                throw new IOException("Failed to stat remote path [" + dest + "]: " + e.getMessage(), e);
            }
        }
        sftpClient.put(local, dest);
    }

    @Override
    public void download(String remote, String dest) throws IOException {
        if (scpTransferMode) {
            scpDownload(remote, dest);
            return;
        }
        sftpClient.get(remote, dest);
    }

    @Override
    public void scpUpload(String local, String dest) throws IOException {
        File localFile = new File(local);
        if (!localFile.exists() || !localFile.canRead()) {
            throw new FileNotFoundException("local file not found: " + local);
        }

        // 标准化远程目录，避免后续拼接出重复斜杠。
        String normalizedDest = normalizeRemoteDir(dest);

        // SCP 协议不会自动创建目标目录，上传临时文件前必须先创建目录。
        mkdirs(normalizedDest);

        // 先上传到临时文件，避免覆盖正在运行的可执行文件时报 "Text file busy"。
        // 上传完成后通过 mv 原子替换目标文件，正在运行的旧进程仍可继续使用旧 inode。
        String fileName = localFile.getName();
        String finalRemotePath = joinRemotePath(normalizedDest, fileName);
        String tmpRemotePath = joinRemotePath(normalizedDest, "." + fileName + ".easy-deploy.tmp");

        scpFileTransfer.upload(local, tmpRemotePath);

        // 将临时文件原子重命名为最终文件；失败时清理临时文件，避免远端残留。
        SshStatus mv = execute("mv -f " + shellQuote(tmpRemotePath) + " " + shellQuote(finalRemotePath));
        if (!mv.isSuccess()) {
            execute("rm -f " + shellQuote(tmpRemotePath));
            throw new IOException("Uploaded to temp but failed to rename to [" + finalRemotePath + "]: " + mv.getMessage());
        }
    }

    @Override
    public void scpDownload(String remote, String dest) throws IOException {
        scpFileTransfer.download(remote, dest);
    }

    @Override
    public SshStatus execute(String cmd) throws IOException {
        Session session = this.sshClient.startSession();
        try {
            Session.Command command = session.exec(cmd);

            String result = IOUtils.readFully(command.getInputStream()).toString();
            String err = IOUtils.readFully(command.getErrorStream()).toString();
            command.close();

            boolean success = server.isCommandSuccess(command.getExitStatus());
            String msg = success ? result : err;
            return new SshStatus(success, msg);
        } finally {
            close(session);
        }
    }

    @Override
    public SshStatus execute(String cmd, CommandLog commandLog) throws IOException {
        Session session = this.sshClient.startSession();
        try {
            Session.Command command = session.exec(cmd);

            String result = IOUtils.readFully(command.getInputStream()).toString();
            String err = IOUtils.readFully(command.getErrorStream()).toString();
            command.close();

            // Output command result to console
            if (!result.isEmpty()) {
                commandLog.println(result.trim());
            }
            if (!err.isEmpty()) {
                commandLog.println(err.trim());
            }

            boolean isOk = command.getExitStatus() == 0;
            String msg = isOk ? result : err;
            return new SshStatus(isOk, msg);
        } finally {
            close(session);
        }
    }

    @Override
    public FutureTask<Void> executeAsync(CommandLog commandLog,
                                         String cmd,
                                         boolean closeAfterFinished) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            Session session = this.sshClient.startSession();
            try {
                Session.Command command = session.exec(cmd);
                InputStream std = command.getInputStream();
                InputStream err = command.getErrorStream();
                for (; ; ) {
                    BufferedReader stdReader = new BufferedReader(new InputStreamReader(std));
                    BufferedReader errReader = new BufferedReader(new InputStreamReader(err));

                    String msg;
                    while ((msg = stdReader.readLine()) != null) {
                        commandLog.println(msg);
                    }

                    while ((msg = errReader.readLine()) != null) {
                        commandLog.println(msg);
                    }

                    if (session.isOpen()) {
                        TimeUnit.MILLISECONDS.sleep(50);
                        continue;
                    }

                    if (!(std.available() > 0 || err.available() > 0)) {
                        break;
                    }
                }
            } finally {
                close(session);
                if (closeAfterFinished) {
                    this.close();
                }
                printFinished(commandLog);
            }
            return null;
        });
        ApplicationManager.getApplication().executeOnPooledThread(task);
        return task;
    }

    @Override
    public void mkdirs(String dir) throws IOException {
        if (scpTransferMode) {
            SshStatus mkdir = execute("mkdir -p " + shellQuote(dir));
            if (!mkdir.isSuccess()) {
                throw new IOException("Failed to create remote directory [" + dir + "]: " + mkdir.getMessage());
            }
            return;
        }
        sftpClient.mkdirs(dir);
    }

    /**
     * 标准化 SCP 目标目录。
     *
     * <p>函数作用：移除普通目录末尾的斜杠，避免拼接出 "/opt/app//file.jar"
     * 这类重复斜杠路径；同时保留根目录 "/"，因为空字符串不能作为有效的
     * mkdir 目标目录或远程路径前缀。</p>
     *
     * @param dir 上传配置中的远程目录，允许为空或根目录
     * @return 标准化后的远程目录；根目录会原样返回 "/"
     */
    private static String normalizeRemoteDir(String dir) {
        if (dir == null || dir.isEmpty() || "/".equals(dir)) {
            return "/";
        }
        return dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;
    }

    /**
     * 拼接远程目录和远程文件名。
     *
     * <p>函数作用：根据标准化后的远程目录生成 SCP 最终目标路径。
     * 当目录是根目录 "/" 时直接返回 "/文件名"；普通目录则返回
     * "目录/文件名"，避免出现双斜杠。</p>
     *
     * @param dir 标准化后的远程目录，通常来自 {@link #normalizeRemoteDir(String)}
     * @param fileName 需要放到远程目录下的文件名
     * @return 拼接完成的远程文件路径
     */
    private static String joinRemotePath(String dir, String fileName) {
        if ("/".equals(dir)) {
            return "/" + fileName;
        }
        return dir + "/" + fileName;
    }

    /**
     * 为 POSIX 兼容的远端 shell 命令转义单个参数。
     *
     * <p>函数作用：把任意路径字符串转换成一个安全的 shell 参数，
     * 确保空格、单引号和 shell 元字符会按字面量传给远端命令，
     * 不会被远端 shell 当作语法解析。</p>
     *
     * @param value 准备发送给远端 shell 的原始参数值
     * @return 转义后的 shell 参数；当入参为 null 或空字符串时返回 "''"
     */
    private static String shellQuote(String value) {
        if (value == null || value.isEmpty()) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    @Override
    public void close() {
        if (clients != null) {
            log.info("Close ssh connection, size: " + clients.size());
            while (!clients.isEmpty()) {
                try {
                    clients.removeLast().close();
                } catch (Exception ignored) {

                }
            }
        }
    }

    @Override
    public boolean isClosed() {
        return sshClient == null || !sshClient.isConnected();
    }

    private void close(Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void printFinished(CommandLog commandLog) {
        commandLog.info("Finished at: " + LocalDateTime.now());
    }
}
