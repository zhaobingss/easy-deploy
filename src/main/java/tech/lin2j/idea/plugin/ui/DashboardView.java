package tech.lin2j.idea.plugin.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import icons.MyIcons;
import org.jetbrains.annotations.NotNull;
import tech.lin2j.idea.plugin.action.ExportAndImportAction;
import tech.lin2j.idea.plugin.action.GithubAction;
import tech.lin2j.idea.plugin.action.HomePageAction;
import tech.lin2j.idea.plugin.action.NewUpdateThreadAction;
import tech.lin2j.idea.plugin.action.ServerSearchKeyAdapter;
import tech.lin2j.idea.plugin.event.ApplicationListener;
import tech.lin2j.idea.plugin.model.ConfigHelper;
import tech.lin2j.idea.plugin.model.event.TableRefreshEvent;
import tech.lin2j.idea.plugin.ssh.SshServer;
import tech.lin2j.idea.plugin.ui.dialog.HostSettingsDialog;
import tech.lin2j.idea.plugin.ui.dialog.PluginSettingsDialog;
import tech.lin2j.idea.plugin.ui.table.ActionCellEditor;
import tech.lin2j.idea.plugin.ui.table.ActionCellRenderer;
import tech.lin2j.idea.plugin.uitl.MessagesBundle;

import javax.swing.AbstractAction;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author linjinjia
 * @date 2024/4/25 06:45
 */
public class DashboardView extends SimpleToolWindowPanel implements ApplicationListener<TableRefreshEvent> {
    private static final String[] COLUMNS = {"ID", "Address", "Username", "Tag", "Description", "Actions"};

    private JBTable hostTable;
    private ComboBox<String> tagComboBox;
    private SearchTextField searchInput;
    private final Project project;

    public DashboardView(Project project) {
        super(true);
        this.project = project;

        initToolbar();
        initHostTable();
    }

    @Override
    public void onApplicationEvent(TableRefreshEvent event) {
        if (event.isTagRefresh()) {
            refreshTagComboBox();
        }
        loadTableData(event);
    }

    private void initToolbar() {
        initTagComboBox();
        initSearchTextField();

        final JPanel northPanel = new JPanel(new GridBagLayout());

        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new HomePageAction());
        actionGroup.add(new GithubAction());
        actionGroup.addSeparator();
        actionGroup.add(new ExportAndImportAction());
        actionGroup.add(new SettingsAction(MessagesBundle.getText("action.dashboard.plugin-setting.text")));
        actionGroup.add(new RefreshAction(MessagesBundle.getText("action.dashboard.refresh.text")));
        actionGroup.add(new AddHostAction(MessagesBundle.getText("action.dashboard.add-host.text")));
        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DashboardView@Toolbar", actionGroup, true);
        toolbar.setTargetComponent(this);

        northPanel.setBorder(JBUI.Borders.empty(2, 0));
        northPanel.add(tagComboBox, new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE,
                JBUI.emptyInsets(), 0, 0));
        northPanel.add(searchInput, new GridBagConstraints(1, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE,
                JBUI.emptyInsets(), 0, 0));
        northPanel.add(toolbar.getComponent(), new GridBagConstraints(2, 0, 1, 1, 1, 1, GridBagConstraints.EAST, GridBagConstraints.NONE,
                JBUI.emptyInsets(), 0, 0));

        setToolbar(northPanel);
    }

    private void initTagComboBox() {
        tagComboBox = new ComboBox<>();
        tagComboBox.addItemListener(e -> {
            String tag = Objects.toString(e.getItem());
            if (StringUtil.isEmpty(tag)) {
                loadTableData(null);
                return;
            }
            List<SshServer> servers = ConfigHelper.sshServers();
            servers = servers.stream()
                    .filter(s -> Objects.equals(tag, s.getTag()))
                    .collect(Collectors.toList());
            loadTableData(new TableRefreshEvent(servers));
        });

        refreshTagComboBox();
    }

    private void refreshTagComboBox() {
        List<String> tags = new ArrayList<>();
        tags.add("");
        tags.addAll(ConfigHelper.getServerTags());
        tagComboBox.setModel(new CollectionComboBoxModel<>(tags));
    }

    private void initSearchTextField() {
        searchInput = new SearchTextField() {
            @Override
            protected void onFieldCleared() {
                loadTableData(null);
            }
        };
        String text = "IP | Name | Desc";
        StatusText emptyText = searchInput.getTextEditor().getEmptyText();
        emptyText.appendText(text, SimpleTextAttributes.GRAY_ATTRIBUTES);
        searchInput.setToolTipText("IP | Name | Desc");
        searchInput.addKeyboardListener(new ServerSearchKeyAdapter(
                searchInput,
                searchResult -> loadTableData(new TableRefreshEvent(searchResult)))
        );
    }

    /**
     * 初始化主机表格，并启用单行拖拽调整主机顺序的能力。
     *
     * <p>该方法没有参数和返回值；初始化完成后，表格会作为当前视图内容并加载主机数据。</p>
     */
    private void initHostTable() {
        hostTable = new JBTable();
        hostTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hostTable.setDragEnabled(true);
        hostTable.setDropMode(DropMode.INSERT_ROWS);
        hostTable.setTransferHandler(new HostRowTransferHandler());
        hostTable.setToolTipText(MessagesBundle.getText("dashboard.host-table.reorder.tip"));
        bindHostMoveShortcut("moveHostUp", KeyStroke.getKeyStroke("alt UP"), -1);
        bindHostMoveShortcut("moveHostDown", KeyStroke.getKeyStroke("alt DOWN"), 1);

        setContent(new JScrollPane(hostTable));
        loadTableData(null);
    }

    /**
     * 为主机表格绑定一个无障碍键盘移动快捷键，作为拖拽操作的等价替代。
     *
     * <p>该方法没有返回值；绑定完成后，快捷键动作会复用主机拖拽的持久化逻辑。</p>
     *
     * @param actionName Swing 输入映射中使用的唯一动作名称
     * @param shortcut 触发移动的键盘快捷键
     * @param rowOffset 相对移动行数；{@code -1} 表示上移一行，{@code 1} 表示下移一行
     */
    private void bindHostMoveShortcut(String actionName, KeyStroke shortcut, int rowOffset) {
        hostTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(shortcut, actionName);
        hostTable.getActionMap().put(actionName, new AbstractAction() {
            /**
             * 将当前选中的主机行按绑定方向移动一行。
             *
             * <p>该方法没有返回值，移动结果会直接刷新当前表格。</p>
             *
             * @param event Swing 触发的键盘动作事件；事件内容不参与排序计算
             */
            @Override
            public void actionPerformed(ActionEvent event) {
                moveSelectedHost(rowOffset);
            }
        });
    }

    /**
     * 根据刷新事件和当前标签筛选条件重新加载主机表格。
     *
     * <p>当事件携带显式主机列表时，保留搜索或标签切换产生的既有结果；当事件没有携带列表时，
     * 视为新增、移除、复制等普通刷新，并继续应用标签下拉框当前选中的值。该方法没有返回值，
     * 加载结果会直接写入当前视图的主机表格。</p>
     *
     * @param e 表格刷新事件；允许为 {@code null}，事件中的主机列表为 {@code null} 时表示普通刷新
     */
    public void loadTableData(TableRefreshEvent e) {
        List<SshServer> requestedServers = e == null ? null : e.getSshServers();
        String selectedTag = Objects.toString(tagComboBox.getSelectedItem(), "");
        List<SshServer> sshServers = resolveTableServers(ConfigHelper.sshServers(), requestedServers, selectedTag);
        Object[][] data = new Object[sshServers.size()][6];
        for (int i = 0; i < sshServers.size(); i++) {
            SshServer sshServer = sshServers.get(i);
            data[i][0] = sshServer.getId();
            data[i][1] = sshServer.getIp() + ":" + sshServer.getPort();
            data[i][2] = sshServer.getUsername();
            data[i][3] = sshServer.getTag();
            data[i][4] = sshServer.getDescription();
            data[i][5] = sshServer.getId();
        }
        DefaultTableModel tableModel = new DefaultTableModel(data, COLUMNS) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5;
            }
        };

        hostTable.setModel(tableModel);
        hostTable.getEmptyText().setText("No data");
        // hide ID column
        hostTable.removeColumn(hostTable.getColumn("ID"));

        TableColumn actionColumn = hostTable.getColumn("Actions");
        actionColumn.setCellRenderer(new ActionCellRenderer(project));
        actionColumn.setCellEditor(new ActionCellEditor(project));
        actionColumn.setMinWidth(450);
        actionColumn.setMaxWidth(550);
    }

    /**
     * 计算一次表格刷新最终应显示的主机列表。
     *
     * <p>显式列表代表搜索或标签切换结果，必须原样使用；普通刷新没有显式列表，此时按当前标签
     * 过滤完整配置列表。该纯函数不修改传入列表，便于在不启动 IntelliJ 界面的情况下验证刷新规则。</p>
     *
     * @param configuredServers 配置中保存的完整主机列表
     * @param requestedServers 刷新事件指定的主机列表；{@code null} 表示普通刷新
     * @param selectedTag 标签下拉框当前选中的值；空字符串表示显示全部标签
     * @return 本次刷新应显示的主机列表
     */
    static List<SshServer> resolveTableServers(List<SshServer> configuredServers,
                                               List<SshServer> requestedServers,
                                               String selectedTag) {
        if (requestedServers != null) {
            return requestedServers;
        }
        if (StringUtil.isEmpty(selectedTag)) {
            return configuredServers;
        }
        return configuredServers.stream()
                .filter(server -> Objects.equals(selectedTag, server.getTag()))
                .collect(Collectors.toList());
    }

    /**
     * 将拖拽后的可见主机顺序合并回完整配置列表。
     *
     * <p>仅替换可见主机原本占据的位置，因此按标签或搜索过滤后拖拽不会改变隐藏主机之间的相对顺序。</p>
     *
     * @param configuredServers 配置中保存的完整主机列表
     * @param orderedVisibleIds 拖拽完成后，当前可见主机按界面顺序排列的 ID
     * @return 合并后的新列表；输入包含重复或不存在的 ID 时返回原顺序的副本
     */
    static List<SshServer> mergeVisibleServerOrder(List<SshServer> configuredServers,
                                                   List<Integer> orderedVisibleIds) {
        Map<Integer, SshServer> serversById = configuredServers.stream()
                .collect(Collectors.toMap(SshServer::getId, server -> server, (first, second) -> first));
        Set<Integer> visibleIds = new HashSet<>(orderedVisibleIds);
        if (visibleIds.size() != orderedVisibleIds.size() || !serversById.keySet().containsAll(visibleIds)) {
            return new ArrayList<>(configuredServers);
        }

        List<SshServer> reorderedServers = new ArrayList<>(configuredServers);
        int visibleIndex = 0;
        for (int i = 0; i < reorderedServers.size(); i++) {
            if (visibleIds.contains(reorderedServers.get(i).getId())) {
                reorderedServers.set(i, serversById.get(orderedVisibleIds.get(visibleIndex++)));
            }
        }
        return reorderedServers;
    }

    /**
     * 把一个主机移动到当前表格的指定插入位置，并将结果写回持久化主机列表。
     *
     * @param draggedServerId 被拖动主机的 ID
     * @param dropRow JTable 提供的目标插入行，允许等于当前行数以表示插入末尾
     * @return {@code true} 表示顺序已改变并完成刷新，{@code false} 表示目标无效或顺序未变化
     */
    private boolean moveHost(int draggedServerId, int dropRow) {
        List<Integer> visibleIds = new ArrayList<>();
        for (int row = 0; row < hostTable.getRowCount(); row++) {
            visibleIds.add((Integer) hostTable.getModel().getValueAt(row, 0));
        }

        int sourceRow = visibleIds.indexOf(draggedServerId);
        if (sourceRow < 0 || dropRow < 0 || dropRow > visibleIds.size()) {
            return false;
        }
        int targetRow = dropRow > sourceRow ? dropRow - 1 : dropRow;
        if (targetRow == sourceRow) {
            return false;
        }

        visibleIds.remove(sourceRow);
        visibleIds.add(targetRow, draggedServerId);

        List<SshServer> reorderedServers = mergeVisibleServerOrder(ConfigHelper.sshServers(), visibleIds);
        ConfigHelper.setSshServerOrder(reorderedServers);

        List<SshServer> visibleServers = visibleIds.stream()
                .map(ConfigHelper::getSshServerById)
                .collect(Collectors.toList());
        loadTableData(new TableRefreshEvent(visibleServers));
        hostTable.setRowSelectionInterval(targetRow, targetRow);
        return true;
    }

    /**
     * 将当前选中的主机向上或向下移动一行，并复用拖拽操作的持久化逻辑。
     *
     * <p>该方法没有返回值；选中行或目标位置无效时保持当前顺序不变。</p>
     *
     * @param rowOffset 相对移动行数；仅支持 {@code -1} 或 {@code 1}
     */
    private void moveSelectedHost(int rowOffset) {
        int sourceRow = hostTable.getSelectedRow();
        int targetRow = sourceRow + rowOffset;
        if (sourceRow < 0 || targetRow < 0 || targetRow >= hostTable.getRowCount()) {
            return;
        }
        int serverId = (Integer) hostTable.getModel().getValueAt(sourceRow, 0);
        int dropRow = targetRow > sourceRow ? targetRow + 1 : targetRow;
        moveHost(serverId, dropRow);
    }

    /**
     * 处理主机表格内部的单行拖放，并把拖动行 ID 作为本地文本数据传递。
     */
    private class HostRowTransferHandler extends TransferHandler {
        private Integer draggedServerId;

        /**
         * 为当前选中行创建拖拽数据。
         *
         * @param component 发起拖拽的 Swing 组件，预期为当前主机表格
         * @return 包含主机 ID 的可传输文本；没有选中行时返回 {@code null}
         */
        @Override
        protected Transferable createTransferable(JComponent component) {
            int selectedRow = hostTable.getSelectedRow();
            if (selectedRow < 0) {
                return null;
            }
            draggedServerId = (Integer) hostTable.getModel().getValueAt(selectedRow, 0);
            return new StringSelection(String.valueOf(draggedServerId));
        }

        /**
         * 声明拖拽仅移动现有行，不复制主机配置。
         *
         * @param component 发起拖拽的 Swing 组件
         * @return {@link TransferHandler#MOVE}，表示移动操作
         */
        @Override
        public int getSourceActions(JComponent component) {
            return MOVE;
        }

        /**
         * 判断当前拖放是否由主机表格内部发起并已记录被拖动的主机 ID。
         *
         * @param support Swing 提供的拖放上下文
         * @return {@code true} 表示可以在当前行间插入，{@code false} 表示拒绝拖放
         */
        @Override
        public boolean canImport(TransferSupport support) {
            return support.getComponent() == hostTable
                    && support.isDrop()
                    && draggedServerId != null;
        }

        /**
         * 读取被拖动主机 ID，并按 JTable 的插入行执行持久化移动。
         *
         * @param support Swing 提供的拖放数据和目标位置
         * @return {@code true} 表示移动成功，{@code false} 表示数据无效或位置没有变化
         */
        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            int dropRow = ((JTable.DropLocation) support.getDropLocation()).getRow();
            return moveHost(draggedServerId, dropRow);
        }

        /**
         * 在拖拽结束后清除内部主机 ID，防止外部文本拖放被误识别为主机移动。
         *
         * <p>该方法没有返回值，清理后下一次移动必须重新从当前表格发起。</p>
         *
         * @param source 发起拖拽的 Swing 组件
         * @param data 本次拖拽使用的传输数据
         * @param action 本次拖拽最终执行的动作类型
         */
        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            draggedServerId = null;
        }
    }

    private class RefreshAction extends NewUpdateThreadAction {

        public RefreshAction(String text) {
            super(text, "Refresh host table", MyIcons.Actions.Refresh);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            refreshTagComboBox();
            loadTableData(null);
        }
    }

    private class AddHostAction extends NewUpdateThreadAction {
        public AddHostAction(String text) {
            super(text, "Add new host profile", MyIcons.Actions.AddHost);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            new HostSettingsDialog(project, null).show();
        }
    }

    private class SettingsAction extends NewUpdateThreadAction {
        public SettingsAction(String text) {
            super(text, "Update plugin settings", MyIcons.Actions.Settings);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            PluginSettingsDialog.show(project);
        }
    }
}
