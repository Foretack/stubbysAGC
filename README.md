###Changes:


####Additions:
`/mute` -> mutes all warnings.
`/unmute` -> unmutes warnings.
`/lastalert` or `/la` -> sets freecam on the location of the last alert.
`/auto 6 [p] [la]` or `/a 6 [p] [la]` -> Goes to cursor. If `p` is found then use persist mode. If `la` is found then will take you to the coordinates of the last alert.
`/copy <target>` -> copies target raw name.
And several other changes from pull requests.

####Shortcuts:
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

####Notable tweaks:
`/pi` will copy player's trace
`/autoban <on/off>` -> will automatically ban people who join with `nigger`, `volas`, `nexit` and `xDdos` in their name. As well as people who say `Nexity#2671`, `nigger` and `test disabled` in the chat. Their info will be sent to the in-game relay channel.
Game UI has been modified heavily.
_The sprites for: Reaper, Lich, Revenant, Draug factory, Dagger factory, Crawler factory, Wraith factory and inverted sorter have been modified._
