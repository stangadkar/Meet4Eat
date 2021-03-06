/*
 * Copyright (c) 2017-2019 by Botorabi. All rights reserved.
 * https://github.com/botorabi/Meet4Eat
 *
 * License: MIT License (MIT), read the LICENSE text in
 *          main directory for more details.
 */
package net.m4e.app.event.business;

import net.m4e.app.communication.ConnectedClients;
import net.m4e.app.event.rest.comm.EventCmd;
import net.m4e.app.mailbox.business.*;
import net.m4e.app.resources.*;
import net.m4e.app.user.business.*;
import net.m4e.common.*;
import net.m4e.system.core.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.*;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.util.*;


/**
 * A collection of event related utilities
 *
 * @author boto
 * Date of creation Sep 4, 2017
 */
@ApplicationScoped
public class Events {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Users users;

    private final Entities entities;

    private final AppInfos appInfos;

    private final Mails mails;

    private final DocumentPool documentPool;

    private final ConnectedClients connectedClients;

    /**
     * Default constructor needed by the container.
     */
    protected Events() {
        entities = null;
        users = null;
        appInfos = null;
        mails = null;
        documentPool = null;
        connectedClients = null;
    }

    /**
     * Create the Events instance.
     */
    @Inject
    public Events(@NotNull Entities entities,
                  @NotNull Users users,
                  @NotNull AppInfos appInfos,
                  @NotNull Mails mails,
                  @NotNull DocumentPool documentPool,
                  @NotNull ConnectedClients connectedClients) {
        this.entities = entities;
        this.users = users;
        this.appInfos = appInfos;
        this.mails = mails;
        this.documentPool = documentPool;
        this.connectedClients = connectedClients;
    }

    /**
     * Create a new event entity basing on data in given input entity.
     * 
     * @param inputEntity   Input data for new entity
     * @param creatorID     ID of creator
     * @return              New created entity
     */
    public EventEntity createNewEvent(EventEntity inputEntity, Long creatorID) {
        // setup the new entity
        EventEntity newEvent = new EventEntity();
        newEvent.setName(inputEntity.getName());
        newEvent.setDescription(inputEntity.getDescription());
        newEvent.setEventStart(inputEntity.getEventStart());
        newEvent.setRepeatWeekDays(inputEntity.getRepeatWeekDays());
        newEvent.setRepeatDayTime(inputEntity.getRepeatDayTime());
        newEvent.setVotingTimeBegin(inputEntity.getVotingTimeBegin());

        if (inputEntity.getPhoto() != null) {
            updateEventImage(newEvent, inputEntity.getPhoto());
        }

        // setup the status
        StatusEntity status = new StatusEntity();
        status.setIdCreator(creatorID);
        status.setIdOwner(creatorID);
        Date now = new Date();
        status.setDateCreation(now.getTime());
        status.setDateLastUpdate(now.getTime());
        newEvent.setStatus(status);

        createEventEntity(newEvent);

        return newEvent;
    }

    /**
     * Given an event entity filled with all its fields, create it in database.
     * 
     * @param event         Event entity
     */
    public void createEventEntity(EventEntity event) {
        // photo and members are shared objects, so remove them before event creation
        DocumentEntity photo = event.getPhoto();
        event.setPhoto(null);
        Collection<UserEntity> members = event.getMembers();
        event.setMembers(null);

        entities.create(event);

        // now re-add photo and members to event entity and update it
        event.setPhoto(photo);
        event.setMembers(members);

        entities.update(event);
    }

    /**
     * Delete the given event entity permanently from database.
     * 
     * @param event         Event entity
     */
    public void deleteEvent(EventEntity event) {
        entities.delete(event);
    }

    /**
     * Update event.
     * 
     * @param event       Event entity to update
     */
    public void updateEvent(EventEntity event) {
        entities.update(event);
    }

    /**
     * Try to find an event with given user ID.
     * 
     * @param id Event ID
     * @return Return an entity if found, otherwise return null.
     */
    public EventEntity findEvent(Long id) {
        EventEntity event = entities.find(EventEntity.class, id);
        return event;
    }

    /**
     * Try to find a location in an event. If the location was not found or is not active
     * then return null.
     * 
     * @param eventId       ID of the event to search for locations
     * @param locationId    ID of the location to find
     * @return              Return the location entity if it was found and it is active, otherwise null.
     */
    public EventLocationEntity findEventLocation(Long eventId, Long locationId) {
        EventEntity event = entities.find(EventEntity.class, eventId);
        if ((event == null) || !event.getStatus().getIsActive()){
            return null;
        }
        // go through the event locations and check if the given locationId is found among the event locations and is active
        EventLocationEntity loc = null;
        if (event.getLocations() != null) {
            for(EventLocationEntity l: event.getLocations()) {
                if (l.getStatus().getIsActive() && (Objects.equals(l.getId(), locationId))) {
                    loc = l;
                    break;
                }
            }
        }
        return loc;
    }

    /**
     * Update the event image with the content of given image.
     * 
     * @param event         Event entity
     * @param image         Image to set to given event
     */
    public void updateEventImage(EventEntity event, DocumentEntity image) {
        // make sure that the resource URL is set
        image.setResourceURL("/Event/Image");
        documentPool.updatePhoto(event, image);
    }

    /**
     * Check if the given user is owner or member of an event.
     * 
     * @param user      User to check
     * @param event     Event
     * @return          Return true if the user is owner or member of given event, otherwise return false.
     */
    public boolean getUserIsEventOwnerOrMember(UserEntity user, EventEntity event) {
        boolean owner = Objects.equals(user.getId(), event.getStatus().getIdOwner());
        if (!owner && (event.getMembers() != null)) {
            if (event.getMembers().stream().anyMatch((u) -> (Objects.equals(u.getId(), user.getId())))) {
                return true;
            } 
        }
        return owner;
    }

    /**
     * Given an event ID return the IDs of all of its members (including the owner). If the event was not
     * found then an empty set is returned.
     * 
     * @param eventId   Event ID
     * @return          A set with member IDs
     */
    public Set<Long> getMembers(Long eventId) {
        Set<Long> memberids = new HashSet();
        EventEntity event = findEvent(eventId);
        if ((event == null) || !event.getStatus().getIsActive()) {
            return memberids;
        }

        Collection<UserEntity> members = event.getMembers();
        // avoid duplicate IDs by using a set (the sender can be also the owner or part of the members)
        memberids.add(event.getStatus().getIdOwner());
        if (members != null) {
            members.forEach((m) -> {
                memberids.add(m.getId());
            });
        }
        return memberids;
    }

    /**
     * Add an user to given event.
     * 
     * @param event       Event
     * @param userToAdd   User to add
     * @throws Exception  Throws exception if any problem occurred.
     */
    public void addMember(EventEntity event, UserEntity userToAdd) throws Exception {
        Collection<UserEntity> members = event.getMembers();
        if (members == null) {
            members = new ArrayList<>();
            event.setMembers(members);
        }
        if (members.contains(userToAdd)) {
            throw new Exception("User is already an event member.");
        }
        if (Objects.equals(userToAdd.getId(), event.getStatus().getIdOwner())) {
            throw new Exception("User is event owner.");            
        }
        members.add(userToAdd);
        updateEvent(event);
    }

    /**
     * Remove an user from given event.
     * 
     * @param event        Event
     * @param userToRemove User to remove
     * @throws Exception   Throws exception if any problem occurred.
     */
    public void removeMember(EventEntity event, UserEntity userToRemove) throws Exception {
        Collection<UserEntity> members = event.getMembers();
        if (members == null) {
            throw new Exception("User is not member of event.");
        }
        if (!members.remove(userToRemove)) {
            throw new Exception("User is not member of event.");            
        }
        updateEvent(event);
    }

    /**
     * Remove any user in given list from an event.
     * 
     * @param event         Event
     * @param usersToRemove Users to remove
     * @throws Exception    Throws exception if any problem occurred.
     */
    public void removeAnyMember(EventEntity event, List<UserEntity> usersToRemove) {
        for (UserEntity user: usersToRemove) {
            Collection<UserEntity> members = event.getMembers();
            if (members == null) {
                continue;
            }
            members.remove(user);
        }
        updateEvent(event);
    }

    /**
     * Mark an event as deleted by setting its status deletion time stamp. This
     * method also updates the system app info entity.
     * 
     * @param event         Event entity
     * @throws Exception    Throws exception if any problem occurred.
     */
    public void markEventAsDeleted(EventEntity event) throws Exception {
        StatusEntity status = event.getStatus();
        if (status == null) {
            throw new Exception("Event has no status field!");
        }
        status.setDateDeletion((new Date().getTime()));
        entities.update(event);

        // update the app stats
        AppInfoEntity appinfo = appInfos.getAppInfoEntity();
        if (appinfo == null) {
            throw new Exception("Problem occurred while retrieving AppInfo entity!");
        }
        appinfo.incrementEventCountPurge(1L);
        entities.update(appinfo);
    }

    /**
     * Get all events which are marked as deleted.
     * 
     * @return List of events which are marked as deleted.
     */
    public List<EventEntity> getEventsMarkedAsDeleted() {
        List<EventEntity> events = entities.findAll(EventEntity.class);
        List<EventEntity> deletedEvents = new ArrayList<>();
        // speed up the task by using parallel processing
        events.stream().parallel()
            .filter((event) -> (event.getStatus().getIsDeleted()))
            .forEach((event) -> {
                deletedEvents.add(event);
            });

        return deletedEvents;
    }

    /**
     * Create a inbox message for a new event member.
     * 
     * @param event     The event
     * @param member    The new member
     */
    public void createEventJoiningMail(EventEntity event, UserEntity member) {
        MailEntity mail = new MailEntity();
        mail.setSenderId(0L);
        mail.setReceiverId(member.getId());
        mail.setReceiverName(member.getName());
        mail.setSendDate((new Date()).getTime());
        mail.setSubject("You joined an event");
        mail.setContent("Hi " + member.getName() + ",\n\nwe wanted to let you know that you joined the event '" +
                                event.getName() + "'.\n\nBest Regards\nMeet4Eat Team\n");
        try {
            mails.createMail(mail);
        }
        catch (Exception ex) {
            LOGGER.warn("*** could not create mail, reason: " + ex.getLocalizedMessage());
        }
    }

    /**
     * Create a inbox message for a member who has left an event. The event is sent
     * to the event owner and the member itself.
     * 
     * @param event     The event
     * @param member    Member who left the event
     */
    public void createEventLeavingMail(EventEntity event, UserEntity member) {
        MailEntity mailUser = new MailEntity();
        mailUser.setSenderId(0L);
        mailUser.setReceiverId(member.getId());
        mailUser.setReceiverName(member.getName());
        mailUser.setSendDate((new Date()).getTime());
        mailUser.setSubject("You have left an event");
        mailUser.setContent("Hi " + member.getName() + ",\n\nwe wanted to confirm that you have left the event '" +
                                event.getName() + "'.\n\nBest Regards\nMeet4Eat Team\n");

        UserEntity ownerEntity = entities.find(UserEntity.class, event.getStatus().getIdOwner());

        MailEntity mailOwner = new MailEntity();
        mailOwner.setSenderId(0L);
        mailOwner.setReceiverId(ownerEntity.getId());
        mailOwner.setReceiverName(ownerEntity.getName());
        mailOwner.setSendDate((new Date()).getTime());
        mailOwner.setSubject("A member has left your event");
        mailOwner.setContent("Hi " + ownerEntity.getName() + ",\n\nwe wanted to let you know that member '" + member.getName() + "' has left your event '" +
                                event.getName() + "'.\n\nBest Regards\nMeet4Eat Team\n");
        try {
            mails.createMail(mailUser);
            mails.createMail(mailOwner);
        }
        catch (Exception ex) {
            LOGGER.warn("*** could not create mail, reason: " + ex.getLocalizedMessage());
        }
    }

    /**
     * Create an event entity out of given data.
     *
     * NOTE: Event members and locations are not imported by this method.
     *
     * @param eventCmd    Data representing an event entity
     * @return            Event entity
     */
    public EventEntity importEvent(@NotNull EventCmd eventCmd) {
        EventEntity eventEntity = new EventEntity();
        eventEntity.setName(eventCmd.getName());
        eventEntity.setDescription(eventCmd.getDescription());
        eventEntity.setIsPublic(eventCmd.getIsPublic());
        eventEntity.setEventStart(eventCmd.getEventStart());
        eventEntity.setRepeatWeekDays(eventCmd.getRepeatWeekDays());
        eventEntity.setRepeatDayTime(eventCmd.getRepeatDayTime());
        eventEntity.setVotingTimeBegin(eventCmd.getVotingTimeBegin());

        if (eventCmd.getPhoto() != null) {
            eventEntity.setPhoto(PhotoCreator.createPhoto(eventCmd.getPhoto().getBytes()));
        }

        return eventEntity;
    }

    /**
     * Export the given event.
     */
    public EventInfo exportEvent(EventEntity event) {
        return EventInfo.fromEventEntity(event, connectedClients, users);
    }
}
