package ray.mintcat.barrier.common

import kotlinx.serialization.Serializable
import org.bukkit.Location
import ray.mintcat.barrier.utils.serializable.LocationSerializer

@Serializable
class LocationPair(
    @Serializable(with = LocationSerializer::class)
    var first: Location?,
    @Serializable(with = LocationSerializer::class)
    var second: Location?
) {
    
    fun isComplete(): Boolean =
        first != null && second != null
    
}