package org.allaymc.server.entity.component;

import lombok.Getter;
import lombok.Setter;
import org.allaymc.api.entity.Entity;
import org.allaymc.api.entity.component.EntityRiderComponent;
import org.allaymc.api.utils.identifier.Identifier;

/**
 * Stores the vehicle currently ridden by an entity.
 */
public class EntityRiderComponentImpl implements EntityRiderComponent {

    @Identifier.Component
    public static final Identifier IDENTIFIER = new Identifier("minecraft:entity_rider_component");

    @Getter
    @Setter
    protected Entity vehicle;
}
