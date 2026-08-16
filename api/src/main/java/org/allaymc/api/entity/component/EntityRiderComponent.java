package org.allaymc.api.entity.component;

import org.allaymc.api.entity.Entity;
import org.jetbrains.annotations.ApiStatus;

/**
 * State held by an entity which can ride another entity.
 */
public interface EntityRiderComponent extends EntityComponent {
    /**
     * Gets the entity currently being ridden.
     *
     * @return the vehicle, or {@code null} if this entity is not riding
     */
    Entity getVehicle();

    /**
     * Checks whether this entity is currently riding another entity.
     *
     * @return {@code true} if this entity is riding, otherwise {@code false}
     */
    default boolean isRiding() {
        return getVehicle() != null;
    }

    /**
     * Updates the other side of a ride relationship. Rideable components are the public entry point.
     *
     * @param vehicle the vehicle being ridden, or {@code null} to clear the relationship
     */
    @ApiStatus.Internal
    void setVehicle(Entity vehicle);
}
