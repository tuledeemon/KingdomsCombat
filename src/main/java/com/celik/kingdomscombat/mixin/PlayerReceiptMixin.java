package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.PenaltyReceipt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.*;
import java.util.UUID;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
public abstract class PlayerReceiptMixin implements PenaltyReceipt {
    @Unique private UUID combat$receipt;
    @Unique private UUID combat$eventReceipt;
    public UUID combat$eventReceipt(){return combat$eventReceipt;}
    public void combat$eventReceipt(UUID id){combat$eventReceipt=id;}
    public UUID combat$penaltyReceipt(){return combat$receipt;}
    public void combat$penaltyReceipt(UUID id){combat$receipt=id;}
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void read(ValueInput input,CallbackInfo ci){String saved=input.getStringOr("kingdomscombat_logout_receipt","");try{combat$receipt=saved.isBlank()?null:UUID.fromString(saved);}catch(IllegalArgumentException e){combat$receipt=null;}try{combat$eventReceipt=UUID.fromString(input.getStringOr("kingdomscombat_event_receipt",""));}catch(IllegalArgumentException e){combat$eventReceipt=null;}}
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void write(ValueOutput output,CallbackInfo ci){if(combat$receipt!=null)output.putString("kingdomscombat_logout_receipt",combat$receipt.toString());if(combat$eventReceipt!=null)output.putString("kingdomscombat_event_receipt",combat$eventReceipt.toString());}
}
