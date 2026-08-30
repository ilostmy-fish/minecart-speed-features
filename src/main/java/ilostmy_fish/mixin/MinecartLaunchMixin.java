package ilostmy_fish.mixin;

import ilostmy_fish.physics.LaunchPhysics;
import ilostmy_fish.rail.RailEndpoint;
import ilostmy_fish.rail.RailRef;
import ilostmy_fish.rail.RailTraversalListener;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Keeps the existing 1.1.0-style ascending-rail launch as a traversal exit feature.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartLaunchMixin extends Entity implements RailTraversalListener {
    @Unique
    private double minecartspeedfeatures$lastVelocity;
    @Unique
    private boolean minecartspeedfeatures$hasLastVelocity;
    @Unique
    private double minecartspeedfeatures$transitionLaunchVelocity;
    @Unique
    private double minecartspeedfeatures$preRailYVelocity;

    protected MinecartLaunchMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    public void minecartspeedfeatures$beginRailTraversal(Vec3d tickVelocity) {
        double currentSample = LaunchPhysics.calculateLaunchSpeed(tickVelocity.length());
        this.minecartspeedfeatures$transitionLaunchVelocity = LaunchPhysics.selectTransitionLaunchSpeed(
                currentSample,
                this.minecartspeedfeatures$lastVelocity,
                this.minecartspeedfeatures$hasLastVelocity
        );
        this.minecartspeedfeatures$lastVelocity = currentSample;
        this.minecartspeedfeatures$hasLastVelocity = true;
        this.minecartspeedfeatures$preRailYVelocity = tickVelocity.getY();
    }

    @Override
    public void minecartspeedfeatures$leaveRail(RailRef rail, RailEndpoint exitEndpoint) {
        if (!rail.shape().isAscending() || exitEndpoint.y() != 0) {
            return;
        }

        this.minecartspeedfeatures$lastVelocity = this.minecartspeedfeatures$transitionLaunchVelocity;
        Vec3d launchBase = this.getVelocity();
        this.setVelocity(
                launchBase.getX(),
                this.minecartspeedfeatures$preRailYVelocity,
                launchBase.getZ()
        );
        this.minecartspeedfeatures$launchFromRail110(rail.shape());
    }

    @Unique
    private void minecartspeedfeatures$launchFromRail110(RailShape lastRail) {
        switch (lastRail) {
            case ASCENDING_EAST, ASCENDING_WEST -> this.addVelocity(
                    (this.minecartspeedfeatures$lastVelocity - 0.0078125)
                            * Math.signum(this.getVelocity().getX()),
                    this.minecartspeedfeatures$lastVelocity * (this.getY() < this.prevY ? -1.0 : 1.0),
                    0.0
            );
            case ASCENDING_NORTH, ASCENDING_SOUTH -> this.addVelocity(
                    0.0,
                    this.minecartspeedfeatures$lastVelocity * (this.getY() < this.prevY ? -1.0 : 1.0),
                    (this.minecartspeedfeatures$lastVelocity - 0.0078125)
                            * Math.signum(this.getVelocity().getZ())
            );
            default -> {
            }
        }
        this.noClip = false;
    }
}
