# ReverseHideAndSeek
Simple reverse hide and seek plugin for Spigot / Bukkit.

## Usage
Simply type `/seek` as admin or `/seek playername` to start the game.
The hider will now have to find a spot and crouch for 5 seconds to start the seeking game.
Once hidden the hider can now have fun keeping tabs on everyone in spectator mode.
The seekers need to find the villager placed on the spot of the hider and hit it.
Once found the seekers can also look in spectator mode.

The game is stopped when all seekers have found the hider. Players are then returned to the starting location.
The game is also stopped when the hider leaves for more than 5 minutes or when there are no seekers left in the game for 5 minutes.
By using the command `/seekman` the game can only be stopped manually using the `/stopseek` command.

The plugin also supports a range (so not the whole server has to participate).
Currently it is really simple and just supports 1 global game in adventuremode.

Note: Plugin will also prevent adventuremode players from destroying paintings.

## Permissions
reversehideandseek.hide: Allows you to hide with the plugin.  
reversehideandseek.seek: Allows you to seek. Can be used to exclude players from the game.  
reversehideandseek.setup: Allows you to setup / start a game. (Default: OP)