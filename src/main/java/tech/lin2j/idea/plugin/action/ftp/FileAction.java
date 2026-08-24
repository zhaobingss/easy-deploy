package tech.lin2j.idea.plugin.action.ftp;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import tech.lin2j.idea.plugin.action.NewUpdateThreadAction;
import tech.lin2j.idea.plugin.file.TableFile;
import tech.lin2j.idea.plugin.ui.ftp.container.FileTableContainer;

import java.util.List;

/**
 *
 *
 * @author linjinjia
 * @date 2024/5/24 21:38
 */
public abstract class FileAction extends NewUpdateThreadAction {

    public FileTableContainer container;

    protected FileAction(String text,  FileTableContainer container) {
        super(text);
        this.container = container;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<TableFile> files = container.getSelectedFiles();
        if (files == null || files.isEmpty()) {
            return;
        }
        handle(files.get(0));
    }

    protected abstract void handle(TableFile file);
}
