package ray.mintcat.barrier.event

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import ray.mintcat.barrier.Barrier
import ray.mintcat.barrier.common.LocationPair
import ray.mintcat.barrier.utils.*
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.ProxyParticle
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptPlayer
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object BarrierListener {

    val createMap = ConcurrentHashMap<UUID, MutableList<Location>>()
    val createRectangleMap = ConcurrentHashMap<UUID, LocationPair>()

    @Awake(LifeCycle.ENABLE)
    fun show() {
        submit(async = true, period = 20) {
            createMap.forEach { (uuid, list) ->
                val player = Bukkit.getPlayer(uuid) ?: return@forEach
                list.forEach {
                    sendParticle(player, it)
                }
            }
        }
    }

    private val particle = ProxyParticle.END_ROD
    fun sendParticle(player: Player, location: Location) {
        particle.sendTo(
            adaptPlayer(player),
            taboolib.common.util.Location(
                location.world!!.name, location.x + 0.5, location.y + 1.5, location.z + 0.5
            ),
            count = 2
        )
    }

    @SubscribeEvent
    fun createRectangleInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if (block.type == Material.AIR || event.hand == EquipmentSlot.OFF_HAND) {
            return
        }
        //物品判断
        val item = event.item
        if (item == null || item.type == Material.AIR || item.type != Barrier.getRectangleTool())
            return
        
        val player = event.player
        val uuid = player.uniqueId
        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> {
                val pair = createRectangleMap[uuid]
                if(pair == null) {
                    createRectangleMap[uuid] = LocationPair(block.location, null)
                } else {
                    pair.first = block.location
                }

                player.info("成功选取第 &f一 &7个点")
            }
            
            Action.RIGHT_CLICK_BLOCK -> {
                val pair = createRectangleMap[uuid]
                if(pair == null) {
                    createRectangleMap[uuid] = LocationPair(null, block.location)
                } else {
                    pair.second = block.location
                }
                
                player.info("成功选取第 &f二 &7个点")
            }
            
            else -> {}
        }
    }
    
    @SubscribeEvent
    fun createInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if (block.type == Material.AIR || event.hand == EquipmentSlot.OFF_HAND) {
            return
        }
        //物品判断
        val item = event.item
        if (item == null || item.type == Material.AIR || item.type != Barrier.getTool()) {
            return
        }
        when (event.action) {
            Action.RIGHT_CLICK_BLOCK -> {
                //删除
                if (createMap[event.player.uniqueId] == null || createMap[event.player.uniqueId]!!.size == 0) {
                    event.player.error("你没有设置点")
                    event.isCancelled = true
                    return
                }
                createMap[event.player.uniqueId]!!.removeLast()
                event.player.info("删除成功!")
                event.isCancelled = true
            }
            Action.LEFT_CLICK_BLOCK -> {
                //添加
                if (createMap[event.player.uniqueId] == null || createMap[event.player.uniqueId]?.isEmpty() == true) {
                    createMap[event.player.uniqueId] = mutableListOf()
                    createMap[event.player.uniqueId]!!.add(block.location)
                    event.player.info("成功添加第 &f${createMap[event.player.uniqueId]!!.size} &7个点")
                } else {
                    if (createMap[event.player.uniqueId]!!.contains(block.location)) {
                        event.player.error("此点已包含!")
                        return
                    }
                    if (createMap[event.player.uniqueId]!!.last().world == block.world) {
                        createMap[event.player.uniqueId]!!.add(block.location)
                        event.player.info("成功添加第 &f${createMap[event.player.uniqueId]!!.size} &7个点")
                    } else {
                        event.player.error("请回到 &f${createMap[event.player.uniqueId]!!.last().world?.name}&7 世界")
                        event.isCancelled = true
                        return
                    }
                }
                event.isCancelled = true
            }
            else -> {}
        }
    }

    @SubscribeEvent
    fun join(event: PlayerMoveEvent) {
        val poly = event.to?.getPoly() ?: return
        if (event.from.getPoly() == null) {
            //视为进入一个新的领地
            BarrierPlayerJoinPolyEvent(event.player, poly).apply {
                call()
                event.isCancelled = this.isCancelled
            }
        }
    }

    @SubscribeEvent
    fun leave(event: PlayerMoveEvent) {
        if (event.from.getPoly() != null && event.to?.getPoly() == null) {
            //视为离开一个新的领地
            BarrierPlayerLeavePolyEvent(event.player, event.from.getPoly()!!).apply {
                call()
                event.isCancelled = this.isCancelled
            }
        }
    }

    @SubscribeEvent
    fun onBarrierPlayerJoinPoly(event: BarrierPlayerJoinPolyEvent) {
        val name = event.poly.name
        if(Barrier.config.contains(name) && Barrier.config.contains("$name.Join")) {
            Barrier.config.getDouble("$name.Join.delay").let { delaySecond ->
                Barrier.config.getStringList("$name.Join.message").let { messages ->
                    val delay = (delaySecond * 1000).toLong()

                    thread {
                        messages.forEach {
                            event.player.sendMessage(it.replace('&', '§'))
                            Thread.sleep(delay)
                        }
                    }
                }
            }
        }
        
    }

    @SubscribeEvent
    fun onBarrierPlayerLeavePoly(event: BarrierPlayerLeavePolyEvent) {
        val name = event.poly.name
        if(Barrier.config.contains(name) && Barrier.config.contains("$name.Leave")) {
            Barrier.config.getDouble("$name.Leave.delay").let { delaySecond ->
                Barrier.config.getStringList("$name.Leave.message").let { messages ->
                    val delay = (delaySecond * 1000).toLong()

                    thread {
                        messages.forEach {
                            event.player.sendMessage(it.replace('&', '§'))
                            Thread.sleep(delay)
                        }
                    }
                }
            }
        }
    }

}