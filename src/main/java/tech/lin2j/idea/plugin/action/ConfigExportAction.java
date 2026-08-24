package tech.lin2j.idea.plugin.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import icons.MyIcons;
import org.jetbrains.annotations.NotNull;
import tech.lin2j.idea.plugin.model.ConfigHelper;
import tech.lin2j.idea.plugin.model.ConfigImportExport;
import tech.lin2j.idea.plugin.model.ExportOptions;
import tech.lin2j.idea.plugin.uitl.FileUtil;
import tech.lin2j.idea.plugin.uitl.ImportExportUtil;
import tech.lin2j.idea.plugin.uitl.MessagesBundle;
import tech.lin2j.idea.plugin.uitl.EasyDeployPluginUtil;

import javax.swing.SwingUtilities;

/**
 * @author linjinjia
 * @date 2024/7/17 21:19
 */
public class ConfigExportAction extends NewUpdateThreadAction {

    private static final Logger log = Logger.getInstance(ConfigExportAction.class);
    private static final String text = MessagesBundle.getText("action.dashboard.export-import.export.text");

    public ConfigExportAction() {
        super(text, text, MyIcons.Actions.Export);
    }

    /**
     * 请求导出密码和保存位置，并将当前插件配置加密写入用户选定的 JSON 文件。
     *
     * <p>用户取消密码输入或保存文件对话框时直接结束，不会创建文件；导出失败时显示错误提示。
     * 该方法没有返回值，导出结果会写入保存对话框返回的本地文件。</p>
     *
     * @param e 当前导出操作事件，用于获取所属项目并挂载原生保存文件对话框
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        try {
            String title = MessagesBundle.getText("dialog.ie.password.title");
            String tip = MessagesBundle.getText("dialog.ie.password.export.text");
            String password = Messages.showPasswordDialog(tip, title);
            if (StringUtil.isEmpty(password)) {
                SwingUtilities.invokeLater(() -> {
                    Messages.showWarningDialog("Password is blank", title);
                });
                return;
            }

            String filepath = ConfigHelper.pluginSetting().getDefaultExportImportPath();
            String filename = "EasyDeploy@" + EasyDeployPluginUtil.version() + ".json";
            FileSaverDescriptor descriptor = new FileSaverDescriptor(text, text, "json");
            FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, e.getProject());
            VirtualFileWrapper targetFile = dialog.save(FileUtil.virtualFile(filepath), filename);
            if (targetFile == null) {
                return;
            }

            ExportOptions options = ConfigHelper.pluginSetting().getExportOptions();
            ConfigImportExport dto = ImportExportUtil.exportBaseOnOptions(options);
            ImportExportUtil.exportConfigToJsonFile(dto, targetFile.getFile().getAbsolutePath(), password);
        } catch (Exception ex) {
            log.error(ex);
            SwingUtilities.invokeLater(() -> Messages.showErrorDialog("Export failed", "Export Error"));
        }
    }
}
