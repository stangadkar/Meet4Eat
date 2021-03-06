/*
 * Copyright (c) 2017-2019 by Botorabi. All rights reserved.
 * https://github.com/botorabi/Meet4Eat
 *
 * License: MIT License (MIT), read the LICENSE text in
 *          main directory for more details.
 */
package net.m4e.common;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;


/**
 * Application wide entity manager provider.
 *
 * @author boto
 * Date of creation Dec 21, 2017
 */
@ApplicationScoped
public class EntityManagerProvider {

    /**
     * The unit name for persistence as configured in persistence.xml
     */
    public final static String PERSISTENCE_UNIT_NAME = "Meet4EatPU";

    /**
     * Injectable Entity-Manager.
     */
    @PersistenceContext(unitName = PERSISTENCE_UNIT_NAME)
    private EntityManager entityManager;

    /**
     * Entity manager producer.
     *
     * @return The entity manager
     */
    @Produces
    public EntityManager getEntityManager() {
        return entityManager;
    }
}
