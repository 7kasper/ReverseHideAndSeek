package nl.kaspermuller.reversehideandseek;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class ReverseHideAndSeek extends JavaPlugin implements Listener, CommandExecutor {

	// Villager to keep track of hiding location.
	Villager phil = null;
	public Map<UUID, SEEK_ROLE> participants = new HashMap<UUID, SEEK_ROLE>();
	public int sneakTask = -1;
	public int stopTaskHider = -1;
	public int stopTaskSeeker = -1;
	public int joinRange = -1;
	Location startLocation = null;
	boolean stopManually = false;
	public Map<UUID, Location> offlineClog = new HashMap<UUID, Location>();

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		this.getCommand("seek").setExecutor(this);
		this.getCommand("stopseek").setExecutor(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!sender.hasPermission("reversehideandseek.setup")) {
			sender.sendMessage(ChatColor.DARK_RED + "You do not have permission to manage a hide and seek game!");
			return true;
		}
		switch(cmd.getLabel()) {
			case "seekman": {
				if (!participants.isEmpty()) {
					sender.sendMessage(ChatColor.DARK_RED + "Seek already in progress, please stop first using /stopseek!");
					return true;
				}
				stopManually = true;
				// no break. Also do the seek part.
			}
			case "seek": {
				if (!participants.isEmpty()) {
					sender.sendMessage(ChatColor.DARK_RED + "Seek already in progress, please stop first using /stopseek!");
					return true;
				}
				if (Bukkit.getOnlinePlayers().size() < 2) {
					sender.sendMessage(ChatColor.DARK_RED + "You need to have at least 2 players to play hide and seek!");
					return true;
				}
				Player hider = null;
				String playerName = "";
				if (args.length < 1) {
					hider = Bukkit.getOnlinePlayers().stream().skip((int) (Bukkit.getOnlinePlayers().size() * Math.random())).findFirst().orElse(null);
				} else {
					playerName = args[0];
					for (Player p : Bukkit.getOnlinePlayers()) {
						if(p.getName().contentEquals(playerName.trim())) {
							hider = p; break;
						}
						if(p.getName().contains(playerName.trim())) {
							hider = p;
						}
					}
				}
				if (hider == null) {
					sender.sendMessage(ChatColor.DARK_RED + "Cannot find player " + playerName);
					return true;
				}
				if (!hider.hasPermission("reversehideandseek.hide")) {
					sender.sendMessage(ChatColor.DARK_RED + "Player " + playerName + " does not have permission to hide!");
					return true;
				}
				if (sender instanceof Player) {
					startLocation = ((Player) sender).getLocation();
				} else {
					startLocation = hider.getLocation();
				}
				participants.put(hider.getUniqueId(), SEEK_ROLE.HIDING);
				hider.setGameMode(GameMode.ADVENTURE);
				hider.setGlowing(false);
				Bukkit.broadcastMessage(ChatColor.YELLOW + "We are searching for " + ChatColor.WHITE + hider.getName() + ChatColor.YELLOW + "!");
				hider.sendMessage(ChatColor.BLUE + "Quickly, hide! Crouch when you have found a good spot.");
				// Also put others into seeker role
				for (Player p : Bukkit.getOnlinePlayers()) {
					if (p.getUniqueId() == hider.getUniqueId()) continue;
					if (!p.hasPermission("reversehideandseek.seek")) continue;
					if (args.length < 2) {
						participants.put(p.getUniqueId(), SEEK_ROLE.SEEKER);
						p.setGameMode(GameMode.ADVENTURE);
						p.setGlowing(true);
					} else {
						try {
							Integer range = Integer.valueOf(args[1]);
							joinRange = range;
							if (p.getLocation().distance(startLocation) > range) {
								participants.put(p.getUniqueId(), SEEK_ROLE.NOT_PARTICIPATING);
							} else {
								participants.put(p.getUniqueId(), SEEK_ROLE.SEEKER);
								p.setGameMode(GameMode.ADVENTURE);
								p.setGlowing(true);
							}
						} catch (NumberFormatException e) {
							sender.sendMessage(ChatColor.DARK_RED + "Not a valid range: " + args[1]);
						}
					}
				}
				break;
			}
			case "stopseek": {
				stopGame(sender);
				break;
			}
		}
		return true;
	}

	@EventHandler
	public void onSneakToggle(PlayerToggleSneakEvent e) {
		if (participants.containsKey(e.getPlayer().getUniqueId()) && participants.get(e.getPlayer().getUniqueId()) == SEEK_ROLE.HIDING) {
			if (sneakTask != -1) {
				Bukkit.getScheduler().cancelTask(sneakTask);
				sneakTask = -1;
			}
			UUID hidingPlayer = e.getPlayer().getUniqueId();
			if (e.isSneaking()) {
				e.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("Keep crouching for 5 seconds to finish hiding."));
				sneakTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
					if (participants.getOrDefault(hidingPlayer, SEEK_ROLE.NOT_PARTICIPATING) == SEEK_ROLE.HIDING) {						
						Player hidden = Bukkit.getPlayer(hidingPlayer);
						if (hidden != null) {
							participants.put(hidingPlayer, SEEK_ROLE.HIDDEN);
							hidden.setGameMode(GameMode.SPECTATOR);
							phil = (Villager) hidden.getWorld().spawnEntity(hidden.getLocation(), EntityType.VILLAGER, true);
							phil.setAI(false);
							phil.setGravity(false);
							phil.setCustomName(hidden.getName());
							phil.setCustomNameVisible(false);
							Bukkit.broadcastMessage(ChatColor.WHITE + hidden.getName() + ChatColor.YELLOW + " has hidden. Let the hunt begin!");
						}
					}
					sneakTask = -1;
				}, 100);	
			}
		}
	}

	@EventHandler
	public void onHitPhil(EntityDamageByEntityEvent e) {
		if (e.getEntity() == phil) {
			if (e.getDamager() instanceof Player) {
				Player finder = (Player) e.getDamager();
				if (participants.getOrDefault(finder.getUniqueId(), SEEK_ROLE.NOT_PARTICIPATING) == SEEK_ROLE.SEEKER) {
					participants.put(finder.getUniqueId(), SEEK_ROLE.FINDER);
					Bukkit.broadcastMessage(finder.getName() + " found the target!");
					AtomicInteger seekersLeft = new AtomicInteger(0);
					participants.forEach((pu, role) -> {
						Player p = Bukkit.getPlayer(pu);
						if (p != null && role != SEEK_ROLE.NOT_PARTICIPATING) {
							p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_HURT, 1, 0);
							if (role == SEEK_ROLE.SEEKER) seekersLeft.addAndGet(1);
						}
					});
					finder.setGlowing(false);
					finder.setGameMode(GameMode.SPECTATOR);
					if (!stopManually && seekersLeft.get() == 0) {
						stopGame(null);
					}
				}

			}
			e.setCancelled(true);
		}
	}

	public void stopIfNeeded() {
		if (stopManually) return;
		AtomicBoolean hiderLeft = new AtomicBoolean(false);
		AtomicInteger seekersLeft = new AtomicInteger(0);
		participants.forEach((pu, role) -> {
			Player p = Bukkit.getPlayer(pu);
			if (p != null && role != SEEK_ROLE.NOT_PARTICIPATING) {
				if (role == SEEK_ROLE.SEEKER) seekersLeft.addAndGet(1);
				if (role == SEEK_ROLE.HIDDEN || role == SEEK_ROLE.HIDING) hiderLeft.set(true);;
			}
		});
		// If there are no hiders or seekers left stop the game.
		if (hiderLeft.get() == false || seekersLeft.get() == 0) {
			stopTaskHider = -1;
			stopTaskSeeker = -1;
			stopGame(null);
		}
		
	}
	
	public void stopGame(CommandSender sender) {
		if (phil != null) {
			phil.remove();
			phil = null;
		}
		if (participants.isEmpty()) {
			if (sender != null) sender.sendMessage(ChatColor.DARK_RED + "No seek currently in progress!");
			return;
		}
		participants.forEach((pu, role) -> {
			Player p = Bukkit.getPlayer(pu);
			if (p != null && role != SEEK_ROLE.NOT_PARTICIPATING) {
				p.teleport(startLocation);
				p.setGameMode(GameMode.ADVENTURE);
				p.setGlowing(false);
				p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 0);
			} else if (p == null && role != SEEK_ROLE.NOT_PARTICIPATING) {
				// Offline player that was in the game.. Make sure it is reset upon relog.
				offlineClog.put(pu, startLocation);
			}
		});
		startLocation = null;
		if (sneakTask != -1) {
			Bukkit.getScheduler().cancelTask(sneakTask);
			sneakTask = -1;
		}
		joinRange = -1;
		stopManually = false;
		participants.clear();
		Bukkit.broadcastMessage(ChatColor.GREEN + "The search has stopped!");
	}

	@EventHandler
	public void onDamagePhil(EntityDamageEvent e) {
		if (e.getEntity() == phil) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onClick(PlayerInteractEvent e) {
		if ((e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) && 
				participants.containsKey(e.getPlayer().getUniqueId()) && e.getPlayer().getGameMode() == GameMode.SPECTATOR) {
			if (phil != null) {
				e.getPlayer().teleport(phil);
				e.getPlayer().setSpectatorTarget(phil);
			}
		}
	}

	// Join as participant
	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		boolean seekersLeft = false;
		if (!participants.isEmpty() && !participants.containsKey(e.getPlayer().getUniqueId()) && e.getPlayer().hasPermission("reversehideandseek.seek")) {
			if (joinRange == -1 || e.getPlayer().getLocation().distance(startLocation) < joinRange) {
				// If joining new game we probably dont need reset we go to adventure anyway.
				offlineClog.remove(e.getPlayer().getUniqueId());
				participants.put(e.getPlayer().getUniqueId(), SEEK_ROLE.SEEKER);
				e.getPlayer().setGameMode(GameMode.ADVENTURE);
				e.getPlayer().setGlowing(true);
				e.getPlayer().sendMessage(ChatColor.YELLOW + "You are playing Hide and Seek. Try to find the target!");
				seekersLeft = true;
			}
		} else if (participants.containsKey(e.getPlayer().getUniqueId())) {
			SEEK_ROLE role = participants.get(e.getPlayer().getUniqueId());
			if (role == SEEK_ROLE.HIDDEN || role == SEEK_ROLE.HIDING) {
				Bukkit.broadcastMessage(ChatColor.RED + "Hider rejoined; game will continue normally.");
				Bukkit.getScheduler().cancelTask(stopTaskHider);
				stopTaskHider = -1;
			} else if (role == SEEK_ROLE.SEEKER) seekersLeft = true;
		}
		// Cancel stop task for no seekers left if there was one.
		if (!stopManually && seekersLeft && stopTaskSeeker != -1) {
			Bukkit.getScheduler().cancelTask(stopTaskSeeker);
			stopTaskSeeker = -1;
		}
		if (offlineClog.containsKey(e.getPlayer().getUniqueId())) {
			// Reset player if we left before game ended.
			e.getPlayer().setGameMode(GameMode.ADVENTURE);
			e.getPlayer().setGlowing(false);
			e.getPlayer().teleport(offlineClog.get(e.getPlayer().getUniqueId()));
			offlineClog.remove(e.getPlayer().getUniqueId());
		}
	}

	@EventHandler
	public void handingDestoy(HangingBreakByEntityEvent e) {
		if (e.getRemover() instanceof Player) {
			Player remover = (Player) e.getRemover();
			if (remover.getGameMode() == GameMode.ADVENTURE) {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onLeave(PlayerQuitEvent e) {
		if (!stopManually) {
			if (participants.containsKey(e.getPlayer().getUniqueId())) {
				SEEK_ROLE role = participants.get(e.getPlayer().getUniqueId());
				// If the hider leaves stop game after 5 min time.
				if (role == SEEK_ROLE.HIDING || role == SEEK_ROLE.HIDDEN) {
					Bukkit.broadcastMessage(ChatColor.RED + "Hider left the game. You can continue but if the player doesn't reconnect the game will stop in 5 minutes.");
					if (stopTaskHider == -1) {
						stopTaskHider = Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> stopIfNeeded(), 6000);
					}
				// If there are no seekers left stop the game after 5 min time.
				} else if (role == SEEK_ROLE.SEEKER) {
					if (stopTaskSeeker == -1) {
						stopTaskSeeker = Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> stopIfNeeded(), 6000);
					}
				}
			}
		}
	}

	enum SEEK_ROLE {
		NOT_PARTICIPATING,
		HIDING,
		HIDDEN,
		SEEKER,
		FINDER
	}

}
