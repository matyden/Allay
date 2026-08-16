package org.allaymc.server.entity.component.vehicle;

import org.allaymc.api.entity.component.EntityRideableComponent;
import org.allaymc.api.entity.data.EntityLinkType;
import org.allaymc.api.entity.interfaces.EntityBoat;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.player.GameMode;
import org.allaymc.api.utils.identifier.Identifier;
import org.allaymc.server.component.annotation.ComponentObject;
import org.allaymc.server.entity.component.event.CEntityBeforeTeleportEvent;
import org.allaymc.server.entity.component.event.CEntityDieEvent;
import org.joml.primitives.AABBd;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Player passenger handling for ordinary boats and bamboo rafts.
 */
public class EntityBoatRideableComponentImpl implements EntityRideableComponent {

    @Identifier.Component
    public static final Identifier IDENTIFIER = new Identifier("minecraft:entity_rideable_component");

    @ComponentObject
    protected EntityBoat boat;

    protected final List<EntityPlayer> passengers = new ArrayList<>(2);

    @Override
    public int getPassengerCapacity() {
        return 2;
    }

    @Override
    public List<EntityPlayer> getPassengers() {
        return List.copyOf(passengers);
    }

    @Override
    public EntityPlayer getControllingPassenger() {
        return passengers.isEmpty() ? null : passengers.getFirst();
    }

    @Override
    public boolean addPassenger(EntityPlayer passenger) {
        if (passenger == null || passenger.getGameMode() == GameMode.SPECTATOR ||
            passenger.getDimension() != boat.getDimension() || passenger.isRiding() ||
            passengers.contains(passenger) || passengers.size() >= getPassengerCapacity()) {
            return false;
        }

        passengers.add(passenger);
        passenger.setVehicle(boat);
        broadcastLink(passenger, passengers.size() == 1 ? EntityLinkType.RIDER : EntityLinkType.PASSENGER);
        boat.broadcastState();
        boat.updatePassengerPositions();
        return true;
    }

    @Override
    public boolean removePassenger(EntityPlayer passenger) {
        var index = passengers.indexOf(passenger);
        if (index < 0) {
            return false;
        }

        boat.setPaddleInput(getControllingPassenger(), null, false, false);
        broadcastLink(passenger, EntityLinkType.REMOVE);
        passengers.remove(index);
        passenger.setVehicle(null);
        movePassengerToDismountPosition(passenger);

        if (index == 0 && !passengers.isEmpty()) {
            var promoted = passengers.getFirst();
            broadcastLink(promoted, EntityLinkType.REMOVE);
            broadcastLink(promoted, EntityLinkType.RIDER);
        }

        boat.broadcastState();
        boat.updatePassengerPositions();
        return true;
    }

    @Override
    public void ejectPassengers() {
        for (var passenger : List.copyOf(passengers)) {
            removePassenger(passenger);
        }
    }

    protected void movePassengerToDismountPosition(EntityPlayer passenger) {
        var yaw = Math.toRadians(boat.getLocation().yaw());
        var sideX = Math.cos(yaw) * 1.5;
        var sideZ = Math.sin(yaw) * 1.5;
        var candidates = new double[][]{
                {boat.getLocation().x() + sideX, boat.getLocation().y(), boat.getLocation().z() + sideZ},
                {boat.getLocation().x() - sideX, boat.getLocation().y(), boat.getLocation().z() - sideZ},
                {boat.getLocation().x() + sideZ, boat.getLocation().y(), boat.getLocation().z() - sideX},
                {boat.getLocation().x() - sideZ, boat.getLocation().y(), boat.getLocation().z() + sideX}
        };
        for (var candidate : candidates) {
            var aabb = passenger.getAABB().translate(candidate[0], candidate[1], candidate[2], new AABBd());
            if (boat.getDimension().getCollidingBlockStates(aabb) == null && !aabb.intersectsAABB(boat.getOffsetAABB())) {
                passenger.trySetLocation(new Location3d(candidate[0], candidate[1], candidate[2],
                        passenger.getLocation().pitch(), passenger.getLocation().yaw(), boat.getDimension()));
                return;
            }
        }
        passenger.trySetLocation(new Location3d(boat.getLocation().x(), boat.getLocation().y() + 0.6, boat.getLocation().z(),
                passenger.getLocation().pitch(), passenger.getLocation().yaw(), boat.getDimension()));
    }

    protected void broadcastLink(EntityPlayer passenger, EntityLinkType linkType) {
        var viewers = new LinkedHashSet<>(boat.getViewers());
        if (passenger.getController() != null) {
            viewers.add(passenger.getController());
        }
        viewers.forEach(viewer -> viewer.viewEntityLink(boat, passenger, linkType));
    }

    @EventHandler
    protected void onBeforeTeleport(CEntityBeforeTeleportEvent event) {
        ejectPassengers();
    }

    @EventHandler
    protected void onDie(CEntityDieEvent event) {
        ejectPassengers();
    }
}
