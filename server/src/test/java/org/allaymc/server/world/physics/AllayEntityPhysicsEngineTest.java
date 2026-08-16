package org.allaymc.server.world.physics;

import org.allaymc.api.entity.Entity;
import org.allaymc.api.entity.interfaces.EntityBoat;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.world.Dimension;
import org.allaymc.testutils.AllayTestExtension;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AllayTestExtension.class)
class AllayEntityPhysicsEngineTest {

    @Test
    void rideableCollisionMotionExcludesPassengers() {
        var dimension = mock(Dimension.class);
        var engine = new TestEntityPhysicsEngine(dimension);
        var passenger = mock(EntityPlayer.class);
        var boat = mockBoat(dimension, List.of(passenger));
        when(passenger.hasEntityCollision()).thenReturn(true);
        when(passenger.getLocation()).thenReturn(new Location3d(0, 0, 0, dimension));

        engine.setCollisions(boat, passenger);
        engine.applyEntityCollisionMotion(boat);

        var motionCaptor = ArgumentCaptor.forClass(Vector3dc.class);
        verify(boat).addMotion(motionCaptor.capture());
        assertEquals(0, motionCaptor.getValue().lengthSquared(), 0.00001);
    }

    @Test
    void rideableCollisionMotionStillIncludesOtherEntities() {
        var dimension = mock(Dimension.class);
        var engine = new TestEntityPhysicsEngine(dimension);
        var boat = mockBoat(dimension, List.of());
        var other = mock(Entity.class);
        when(other.hasEntityCollision()).thenReturn(true);
        when(other.getLocation()).thenReturn(new Location3d(0, 0, 0, dimension));

        engine.setCollisions(boat, other);
        engine.applyEntityCollisionMotion(boat);

        var motionCaptor = ArgumentCaptor.forClass(Vector3dc.class);
        verify(boat).addMotion(motionCaptor.capture());
        assertTrue(motionCaptor.getValue().lengthSquared() > 0);
    }

    private EntityBoat mockBoat(Dimension dimension, List<EntityPlayer> passengers) {
        var boat = mock(EntityBoat.class);
        when(boat.getRuntimeId()).thenReturn(1L);
        when(boat.hasEntityCollision()).thenReturn(true);
        when(boat.getLocation()).thenReturn(new Location3d(1, 0, 0, dimension));
        when(boat.getPushSpeedReduction()).thenReturn(1.0);
        when(boat.getPassengers()).thenReturn(passengers);
        return boat;
    }

    private static final class TestEntityPhysicsEngine extends AllayEntityPhysicsEngine {
        private TestEntityPhysicsEngine(Dimension dimension) {
            super(dimension);
        }

        private void setCollisions(Entity entity, Entity... collisions) {
            entityCollisionCache.put(entity.getRuntimeId(), new ArrayList<>(List.of(collisions)));
        }

        private void applyEntityCollisionMotion(Entity entity) {
            computeEntityCollisionMotion(entity);
        }
    }
}
