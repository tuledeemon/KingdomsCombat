package com.celik.kingdomscombat.mixin;
import com.celik.kingdomscombat.StaffPermissions;
import me.lucko.luckperms.common.cacheddata.CacheMetadata;
import me.lucko.luckperms.common.cacheddata.result.TristateResult;
import me.lucko.luckperms.common.cacheddata.type.PermissionCache;
import me.lucko.luckperms.common.calculator.CalculatorFactory;
import me.lucko.luckperms.common.model.HolderType;
import me.lucko.luckperms.common.verbose.event.CheckOrigin;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value=PermissionCache.class,remap=false)
public abstract class StaffLuckPermsMixin {
    @Shadow @Final private QueryOptions queryOptions;
    @Unique private UUID staff$owner;
    @Inject(method="<init>",at=@At("TAIL")) private void owner(QueryOptions options,CacheMetadata metadata,CalculatorFactory factory,ConcurrentHashMap<String,Node> nodes,CallbackInfo ci){if(metadata.getHolderType()==HolderType.USER)staff$owner=metadata.getVerboseCheckInfo().getId();}
    @Inject(method="checkPermission(Ljava/lang/String;Lme/lucko/luckperms/common/verbose/event/CheckOrigin;)Lme/lucko/luckperms/common/cacheddata/result/TristateResult;",at=@At("HEAD"),cancellable=true)
    private void effective(String node,CheckOrigin origin,CallbackInfoReturnable<TristateResult> cir){var result=StaffPermissions.override(staff$owner,queryOptions,node);if(result!=null)cir.setReturnValue(TristateResult.forMonitoredResult(result));}
    @Inject(method="getPermissionMap",at=@At("HEAD"),cancellable=true) private void map(CallbackInfoReturnable<Map<String,Boolean>> cir){var result=StaffPermissions.overrideMap(staff$owner,queryOptions);if(result!=null)cir.setReturnValue(result);}
}
