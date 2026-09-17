package ir.shahed.pahpad.core
import ir.shahed.pahpad.data.UpgradeKind
/** Persistence double only; mission and collisions run real production Kotlin. */
class SaveManager {
 val selectedDroneId="shahed131"
 val invertPitch=false
 var endlessBest=0
 var dailyBest=0
 fun upgradeLevel(id:String, kind:UpgradeKind)=0
 fun addCoins(n:Int) {}
 fun addXp(n:Int) {}
 fun recordResult(id:String,stars:Int,score:Int,elapsed:Float) {}
}
