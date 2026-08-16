package org.allaymc.server.entity.component.vehicle;

import lombok.Getter;
import org.allaymc.api.entity.EntityInitInfo;
import org.allaymc.api.entity.component.EntityBoatBaseComponent;
import org.allaymc.api.entity.component.EntityPhysicsComponent;
import org.allaymc.api.entity.component.EntityRideableComponent;
import org.allaymc.api.entity.data.BoatVariant;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.item.ItemStack;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.server.component.annotation.Dependency;
import org.allaymc.server.entity.component.EntityBaseComponentImpl;
import org.allaymc.server.entity.component.event.CEntityLoadNBTEvent;
import org.allaymc.server.entity.component.event.CEntitySaveNBTEvent;
import org.allaymc.server.entity.component.event.CEntityTickEvent;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;

import java.util.Objects;

/**
 * Base behavior for ordinary boats and bamboo rafts.
 */
public class EntityBoatBaseComponentImpl extends EntityBaseComponentImpl implements EntityBoatBaseComponent {
    protected static final String TAG_VARIANT = "Variant";
    protected static final int INPUT_EXPIRY_TICKS = 2;
    protected static final double MAX_HORIZONTAL_SPEED = 0.4;

    @Dependency
    protected EntityPhysicsComponent physicsComponent;
    @Dependency
    protected EntityRideableComponent rideableComponent;

    @Getter
    protected BoatVariant boatVariant = BoatVariant.OAK;
    protected final Vector2f movementInput = new Vector2f();
    @Getter
    protected boolean paddlingLeft;
    @Getter
    protected boolean paddlingRight;
    @Getter
    protected float rowTimeLeft;
    @Getter
    protected float rowTimeRight;
    protected long lastInputTick = Long.MIN_VALUE;

    public EntityBoatBaseComponentImpl(EntityInitInfo info) {
        super(info);
    }

    @Override
    public AABBdc getBaseAABB() {
        return new AABBd(-0.7, 0, -0.7, 0.7, 0.455, 0.7);
    }

    @Override
    public void setBoatVariant(BoatVariant variant) {
        this.boatVariant = Objects.requireNonNull(variant, "variant");
        broadcastState();
        updatePassengerPositions();
    }

    @Override
    public boolean onInteract(EntityPlayer player, ItemStack itemStack) {
        return rideableComponent.addPassenger(player);
    }

    @Override
    public void setPaddleInput(EntityPlayer player, Vector2fc movement, boolean paddleLeft, boolean paddleRight) {
        if (player != rideableComponent.getControllingPassenger()) {
            return;
        }

        this.movementInput.set(movement == null ? new Vector2f() : movement);
        this.paddlingLeft = paddleLeft;
        this.paddlingRight = paddleRight;
        this.lastInputTick = getTick();
    }

    @EventHandler
    protected void onTick(CEntityTickEvent event) {
        if (rideableComponent.getControllingPassenger() == null || getTick() - lastInputTick > INPUT_EXPIRY_TICKS) {
            clearInput();
        }

        applyControlInput();
        updateRowingAnimation();
    }

    protected void applyControlInput() {
        if (rideableComponent.getControllingPassenger() == null) {
            return;
        }

        var forward = Math.clamp(movementInput.y(), -1f, 1f);
        var steer = Math.clamp(movementInput.x(), -1f, 1f);
        if (Math.abs(forward) < 0.001f && Math.abs(steer) < 0.001f) {
            if (paddlingLeft && paddlingRight) {
                forward = 1;
            } else if (paddlingLeft != paddlingRight) {
                steer = paddlingRight ? 1 : -1;
            }
        }

        var location = new Location3d(getLocation());
        if (Math.abs(steer) > 0.001f) {
            location.yaw += steer;
            trySetLocation(location);
        }

        double acceleration = forward >= 0 ? 0.04 * forward : 0.02 * forward;
        if (Math.abs(forward) < 0.001f && paddlingLeft != paddlingRight) {
            acceleration = 0.005;
        }
        if (Math.abs(acceleration) < 0.00001) {
            return;
        }

        var yaw = Math.toRadians(location.yaw());
        var motion = physicsComponent.getMotion().add(
                -Math.sin(yaw) * acceleration,
                0,
                Math.cos(yaw) * acceleration,
                new Vector3d()
        );
        var horizontalSpeed = Math.hypot(motion.x(), motion.z());
        if (horizontalSpeed > MAX_HORIZONTAL_SPEED) {
            var scale = MAX_HORIZONTAL_SPEED / horizontalSpeed;
            motion.x *= scale;
            motion.z *= scale;
        }
        physicsComponent.setMotion(motion);
    }

    protected void updateRowingAnimation() {
        var oldLeft = rowTimeLeft;
        var oldRight = rowTimeRight;
        rowTimeLeft = paddlingLeft ? rowTimeLeft + (float) (Math.PI / 8) : 0;
        rowTimeRight = paddlingRight ? rowTimeRight + (float) (Math.PI / 8) : 0;
        if (Float.compare(oldLeft, rowTimeLeft) != 0 || Float.compare(oldRight, rowTimeRight) != 0) {
            broadcastState();
        }
    }

    protected void clearInput() {
        movementInput.zero();
        paddlingLeft = false;
        paddlingRight = false;
    }

    @Override
    public void updatePassengerPositions() {
        var passengers = rideableComponent.getPassengers();
        if (passengers.isEmpty()) {
            return;
        }

        for (int i = 0; i < passengers.size(); i++) {
            var localX = passengers.size() == 1 ? 0 : (i == 0 ? 0.2 : -0.6);
            var localY = boatVariant.isRaft() ? 0.1 : -0.2;
            var yaw = Math.toRadians(getLocation().yaw());
            var x = getLocation().x() + localX * Math.cos(yaw);
            var z = getLocation().z() + localX * Math.sin(yaw);
            var passenger = passengers.get(i);
            passenger.trySetLocation(new Location3d(
                    x, getLocation().y() + localY, z,
                    passenger.getLocation().pitch(), getLocation().yaw() - 90,
                    getDimension()
            ));
        }
    }

    @EventHandler
    protected void onSaveNBT(CEntitySaveNBTEvent event) {
        event.getNbt().putInt(TAG_VARIANT, boatVariant.getNetworkId());
    }

    @EventHandler
    protected void onLoadNBT(CEntityLoadNBTEvent event) {
        if (event.getNbt().containsKey(TAG_VARIANT)) {
            var loaded = BoatVariant.fromNetworkId(event.getNbt().getInt(TAG_VARIANT));
            boatVariant = loaded == null ? BoatVariant.OAK : loaded;
        }
    }

}
