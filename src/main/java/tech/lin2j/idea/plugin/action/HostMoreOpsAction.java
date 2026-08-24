package tech.lin2j.idea.plugin.action;

import com.intellij.openapi.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.lin2j.idea.plugin.event.ApplicationContext;
import tech.lin2j.idea.plugin.model.ConfigHelper;
import tech.lin2j.idea.plugin.model.event.TableRefreshEvent;
import tech.lin2j.idea.plugin.ssh.SshServer;
import tech.lin2j.idea.plugin.ui.dialog.HostSettingsDialog;
import tech.lin2j.idea.plugin.ui.editor.SFTPFileSystem;
import tech.lin2j.idea.plugin.ui.editor.SFTPVirtualFile;
import tech.lin2j.idea.plugin.ui.ftp.FTPConsole;
import tech.lin2j.idea.plugin.uitl.MessagesBundle;
import tech.lin2j.idea.plugin.uitl.UiUtil;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.UUID;

/**
 * @author linjinjia
 * @date 2024/5/5 12:29
 */
public class HostMoreOpsAction implements ActionListener {
    public static final Logger log = LoggerFactory.getLogger(HostMoreOpsAction.class);

    private final int sshId;
    private final Project project;

    private final JButton parent;

    public HostMoreOpsAction(int sshId, Project project, JButton parent) {
        this.sshId = sshId;
        this.project = project;
        this.parent = parent;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JPopupMenu menu = new JPopupMenu();
        String propertiesText = MessagesBundle.getText("table.action.button.more.properties");
        menu.add(new JMenuItem(new AbstractAction(propertiesText) {
            @Override
            public void actionPerformed(ActionEvent e) {
                SshServer server = ConfigHelper.getSshServerById(sshId);
                new HostSettingsDialog(project, server).show();
            }
        }));

        String copyText = MessagesBundle.getText("table.action.button.more.copy");
        JMenuItem copyItem = new JMenuItem(copyText);
        copyItem.addActionListener(event -> copyHost());
        menu.add(copyItem);

        String sftpText = MessagesBundle.getText("table.action.button.more.sftp");
        menu.add(new JMenuItem(new AbstractAction(sftpText) {
            @Override
            public void actionPerformed(ActionEvent e) {
                SshServer server = ConfigHelper.getSshServerById(sshId);
                try {
                    SFTPVirtualFile SFTPVirtualFile = new SFTPVirtualFile(
                            server.getIp(),
                            project,
                            new FTPConsole(project, server)
                    );
                    SFTPFileSystem.getInstance(project).openEditor(SFTPVirtualFile);
                } catch (IOException ex) {
                    log.error(ex.getMessage(), e);
                }
            }
        }));

        String removeText = MessagesBundle.getText("table.action.button.more.remove");
        menu.add(new JMenuItem(new AbstractAction(removeText) {
            @Override
            public void actionPerformed(ActionEvent e) {
                SshServer server = ConfigHelper.getSshServerById(sshId);
                String specific = "Host: " + server.getIp() + ":" + server.getPort();
                if (UiUtil.deleteConfirm(specific)) {
                    ConfigHelper.removeSshServer(sshId);
                    ApplicationContext.getApplicationContext().publishEvent(new TableRefreshEvent());
                }
            }
        }));
        menu.show(parent, 0, parent.getHeight());
    }

    /**
     * 复制当前“更多”菜单所对应的主机配置，并将副本加入主机列表。
     *
     * <p>该方法不接收参数，也不返回值。副本保留连接、认证、标签、代理和描述等主机字段，
     * 但会重新分配数值 ID 与全局唯一 UID，避免覆盖原主机。命令和上传配置不属于主机字段，
     * 因此不会随主机一起复制。完成后发布表格刷新事件，使新主机立即显示在列表中。</p>
     */
    private void copyHost() {
        SshServer copy = ConfigHelper.getSshServerById(sshId).clone();
        copy.setId(ConfigHelper.maxSshServerId() + 1);
        copy.setUid(UUID.randomUUID().toString());
        ConfigHelper.addSshServer(copy);
        ApplicationContext.getApplicationContext().publishEvent(new TableRefreshEvent());
    }
}
