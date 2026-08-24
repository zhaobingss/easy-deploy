package tech.lin2j.idea.plugin.ui;

import org.junit.Test;
import tech.lin2j.idea.plugin.ssh.SshServer;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * 主机列表刷新规则的回归测试。
 */
public class DashboardViewTest {

    /**
     * 验证普通刷新没有携带显式主机列表时，仍会保留用户当前选中的标签过滤条件。
     *
     * <p>该测试方法不接收参数，也不返回值；断言失败表示新增、移除或复制主机后会错误显示全部主机。</p>
     */
    @Test
    public void keepsSelectedTagFilterOnNormalRefresh() {
        SshServer selected = new SshServer();
        selected.setTag("selected");
        SshServer other = new SshServer();
        other.setTag("other");

        List<SshServer> result = DashboardView.resolveTableServers(
                List.of(selected, other),
                null,
                "selected"
        );

        assertEquals(List.of(selected), result);
    }

    /**
     * 验证在过滤结果中拖拽时，只重排可见主机占据的位置，隐藏主机仍保持原有相对顺序。
     *
     * <p>该测试方法不接收参数，也不返回值；断言失败表示切换标签或搜索后拖拽会打乱其他主机。</p>
     */
    @Test
    public void keepsHiddenServersInPlaceWhenReorderingFilteredRows() {
        SshServer firstVisible = new SshServer();
        firstVisible.setId(1);
        SshServer firstHidden = new SshServer();
        firstHidden.setId(2);
        SshServer secondVisible = new SshServer();
        secondVisible.setId(3);
        SshServer secondHidden = new SshServer();
        secondHidden.setId(4);

        List<SshServer> result = DashboardView.mergeVisibleServerOrder(
                List.of(firstVisible, firstHidden, secondVisible, secondHidden),
                List.of(3, 1)
        );

        assertEquals(List.of(secondVisible, firstHidden, firstVisible, secondHidden), result);
    }
}
