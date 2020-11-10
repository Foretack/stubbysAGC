### Changes:

someone pls remind to update this later

#### Additions:

`/blacklist [list|clear] <target>` -> Autobans anyone who joins with `<target>` in their name, e.g `/blacklist unbannable` will auto ban UNBANNABLE ROUTER CHAIN upon join. If `list` is found it will show the current blacklisted names. If `clear` is found it will clear the blacklisted names.

`/chatfilter [list|clear] <target word>` -> Autokicks anyone who sends a message containing specified word. Works exactly like blacklist but with kicks.

`/mute` -> mutes all warnings.

`/unmute` -> unmutes warnings.

`/lastalert` or `/la` -> sets freecam on the location of the last alert.

`/auto 6 [p] [la]` or `/a 6 [p] [la]` -> Goes to cursor. If `p` is found then use persist mode. If `la` is found then will take you to the coordinates of the last alert.

`/copy <target>` -> copies target raw name.

`/copyga <target>` -> copies target's actions log.

`/uuid <target> [--b | --l | --k]` or `/u <target> [--b | --l | --k]` -> copies target uuid. If `b` is found ban sytax will be copied. If `l` is found lookup sytax will be copied. If `k` is found kick sytax will be copied.  

`/lastuuid [b | l | k]` or `/lu [b | l | k]` -> copies the uuid of the last alerter. If `b` is found ban sytax will be copied. If `l` is found lookup sytax will be copied. If `k` is found kick sytax will be copied. 

And several other changes from pull requests.

#### Shortcuts:

Added shortcuts:

`/show <target>` -> `/s <target>`

`/getactions <target>` -> `/ga <target>`

`/undoactions <target>` -> `/ua <target>`

`/auto <target>` -> `/a <target>`

`/auto goto [persist] <X, Y>` -> `/a 1 [p] <X, Y>`

`/auto gotoplayer [assist|follow|undo] <target>` -> `/a 2 [ a | f | u ] <target>`

`/auto pickuptarget <X, Y>/cursor tile` -> `/a 3 <X, Y>/cursor tile`

`/auto dumptarget <X, Y>/cursor tile` -> `/a 4 <X, Y>/cursor tile`

`/auto itemsource [cancel]` -> `/auto is [cancel]`

`/players` -> `/p`

`/fixpower` -> `/fp`


#### Notable tweaks:

`/pi` will copy player's trace

`/autoban <on/off>` -> will automatically ban people who join with `�`and "nexity in their name. Their info will be sent to the in-game relay channel.

Game UI has been modified heavily.

_The sprites for: Reaper, Lich, Revenant, Draug factory, Dagger factory, Crawler factory, Wraith factory and inverted sorter have been modified._
