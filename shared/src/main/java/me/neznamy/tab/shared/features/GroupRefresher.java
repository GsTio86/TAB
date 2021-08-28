package me.neznamy.tab.shared.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.neznamy.tab.api.TabFeature;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.shared.CpuConstants;
import me.neznamy.tab.shared.ITabPlayer;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.permission.LuckPerms;
import me.neznamy.tab.shared.permission.PermissionPlugin;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;

/**
 * Permission group refresher
 */
public class GroupRefresher extends TabFeature {

	public static final String DEFAULT_GROUP = "NONE";
	
	private Object luckPermsSub;
	private PermissionPlugin plugin;
	private boolean groupsByPermissions;
	private List<String> primaryGroupFindingList;
	
	public GroupRefresher(PermissionPlugin plugin) {
		super("Permission group refreshing");
		this.plugin = plugin;
		groupsByPermissions = TAB.getInstance().getConfiguration().getConfig().getBoolean("assign-groups-by-permissions", false);
		primaryGroupFindingList = new ArrayList<>();
		for (Object group : TAB.getInstance().getConfiguration().getConfig().getStringList("primary-group-finding-list", Arrays.asList("Owner", "Admin", "Helper", "default"))){
			primaryGroupFindingList.add(group.toString());
		}
		if (plugin instanceof LuckPerms) {
			luckPermsSub = LuckPermsProvider.get().getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
				long time = System.nanoTime();
				TabPlayer p = TAB.getInstance().getPlayer(event.getUser().getUniqueId());
				if (p == null) return; //server still starting up and users connecting already (LP loading them)
				refreshPlayer(p);
				TAB.getInstance().getCPUManager().addTime(this, CpuConstants.UsageCategory.LUCKPERMS_RECALCULATE_EVENT, System.nanoTime()-time);
			});
		} else {
			TAB.getInstance().getCPUManager().startRepeatingMeasuredTask(1000, "refreshing permission groups", this, CpuConstants.UsageCategory.REFRESHING_GROUPS, () -> {
				for (TabPlayer p : TAB.getInstance().getOnlinePlayers()) refreshPlayer(p);
			});
		}
	}
	
	private void refreshPlayer(TabPlayer p) {
		((ITabPlayer)p).setGroup(detectPermissionGroup(p), true);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void unload() {
		if (luckPermsSub != null) ((EventSubscription<UserDataRecalculateEvent>)luckPermsSub).close();
	}

	public String detectPermissionGroup(TabPlayer p) {
		if (isGroupsByPermissions()) {
			return getByPermission(p);
		}
		return getByPrimary(p);
	}

	private String getByPrimary(TabPlayer p) {
		try {
			return plugin.getPrimaryGroup(p);
		} catch (Exception e) {
			TAB.getInstance().getErrorManager().printError("Failed to get permission group of " + p.getName() + " using " + plugin.getName() + " v" + plugin.getVersion(), e);
			return DEFAULT_GROUP;
		}
	}

	private String getByPermission(TabPlayer p) {
		for (Object group : primaryGroupFindingList) {
			if (p.hasPermission("tab.group." + group)) {
				return String.valueOf(group);
			}
		}
		return DEFAULT_GROUP;
	}

	public boolean isGroupsByPermissions() {
		return groupsByPermissions;
	}
	
	public PermissionPlugin getPlugin() {
		return plugin;
	}
}