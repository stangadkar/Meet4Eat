/**
 * Copyright (c) 2017 by Botorabi. All rights reserved.
 * https://github.com/botorabi/Meet4Eat
 * 
 * License: MIT License (MIT), read the LICENSE text in
 *          main directory for more details.
 */

package net.m4e.app.event;

import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.servlet.http.HttpServletRequest;
import javax.inject.Inject;
import javax.json.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import net.m4e.app.auth.AuthRole;
import net.m4e.app.auth.AuthorityConfig;
import net.m4e.app.communication.ConnectedClients;
import net.m4e.app.notification.NotifyUserRelativesEvent;
import net.m4e.app.notification.NotifyUsersEvent;
import net.m4e.common.ResponseResults;
import net.m4e.system.core.AppInfoEntity;
import net.m4e.system.core.AppInfos;
import net.m4e.system.core.Log;
import net.m4e.app.user.UserEntity;
import net.m4e.app.user.Users;
import net.m4e.common.Entities;

/**
 * REST services for Event entity operations.
 * The results of operations depend on the privileges of authenticated user.
 * 
 * @author boto
 * Date of creation Aug 18, 2017
 */
@Stateless
@Path("/rest/events")
public class EventEntityFacadeREST {

    /**
     * Used for logging
     */
    private final static String TAG = "EventEntityFacadeREST";

    /**
     * Event used for notifying other users
     */
    @Inject
    private Event<NotifyUsersEvent> notifyUsersEvent;

    /**
     * Event used for notifying other users
     */
    @Inject
    private Event<NotifyUserRelativesEvent> notifyUserRelativesEvent;

    /**
     * Central place to hold all client connections
     */
    @Inject
    private ConnectedClients connections;

    /**
     * Entities
     */
    private final Entities entities;

    /**
     * Events
     */
    private final Events events;

    /**
     * Users
     */
    private final Users users;

    /**
     * The event locations
     */
    private final EventLocations eventLocations;

    /**
     * AppInfos
     */
    private final AppInfos appInfos;

    /**
     * Event input validator
     */
    private final EventEntityInputValidator validator;

    
    /**
     * EJB's default constructor
     */
    protected EventEntityFacadeREST() {
        entities = null;
        events = null;
        users = null;
        validator = null;
        eventLocations = null;
        appInfos = null;
    }

    /**
     * Create the event entity REST facade.
     * 
     * @param entities
     * @param events
     * @param users
     * @param validator
     * @param eventLocations
     * @param appInfos
     */
    @Inject
    public EventEntityFacadeREST(Entities entities,
                                 Events events,
                                 Users users,
                                 EventEntityInputValidator validator,
                                 EventLocations eventLocations,
                                 AppInfos appInfos) {

        this.entities = entities;
        this.events = events;
        this.users = users;
        this.validator = validator;
        this.eventLocations = eventLocations;
        this.appInfos = appInfos;
    }

    /**
     * Create a new event.
     * 
     * @param eventJson  Event details in JSON format
     * @param request    HTTP request
     * @return           JSON response
     */
    @POST
    @Path("create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String createEvent(String eventJson, @Context HttpServletRequest request) {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);
        EventEntity reqentity;
        try {
            reqentity = validator.validateNewEntityInput(eventJson);
        }
        catch (Exception ex) {
            Log.warning(TAG, "*** Could not create new event, validation failed, reason: " + ex.getLocalizedMessage());
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, ex.getLocalizedMessage(), ResponseResults.CODE_BAD_REQUEST, jsonresponse.build().toString());
        }

        EventEntity newevent;
        try {
            newevent = events.createNewEvent(reqentity, sessionuser.getId());
        }
        catch (Exception ex) {
            Log.warning(TAG, "*** Could not create new event, reaon: " + ex.getLocalizedMessage());
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to create new event.", ResponseResults.CODE_INTERNAL_SRV_ERROR, jsonresponse.build().toString());
        }

        // notify all event members about its creation, usually only the event owner is the only member at this point
        EventNotifications notifications = new EventNotifications(notifyUsersEvent, notifyUserRelativesEvent);
        notifications.sendNotifyEventChanged(EventNotifications.ChangeType.Add, AuthorityConfig.getInstance().getSessionUser(request), newevent);

        //! NOTE on successful entity creation the new event ID is sent back by results.data field.
        jsonresponse.add("id", newevent.getId().toString());
        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Event was successfully created.", ResponseResults.CODE_OK, jsonresponse.build().toString());
    }

    /**
     * Modify the event with given ID.
     * 
     * @param id        Event ID
     * @param eventJson Entity modifications in JSON format
     * @param request   HTTP request
     * @return          JSON response
     */
    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String edit(@PathParam("id") Long id, String eventJson, @Context HttpServletRequest request) {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        jsonresponse.add("id", id.toString());
        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);
        EventEntity reqentity = events.importEventJSON(eventJson);
        if (reqentity == null) {
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to update event, invalid input.", ResponseResults.CODE_BAD_REQUEST, jsonresponse.build().toString());
        }

        EventEntity event = entities.find(EventEntity.class, id);
        if (event == null) {
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to find event for updating.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        // check if the event owner or a user with higher privilege is trying to modify the event
        if (!users.userIsOwnerOrAdmin(sessionuser, event.getStatus())) {
            Log.warning(TAG, "*** User was attempting to update an event without proper privilege!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to update event, insufficient privilege.", ResponseResults.CODE_FORBIDDEN, jsonresponse.build().toString());
        }

        // take over non-empty fields
        if ((reqentity.getName() != null) && !reqentity.getName().isEmpty()) {
            event.setName(reqentity.getName());
        }
        if ((reqentity.getDescription() != null) && !reqentity.getDescription().isEmpty()) {
            event.setDescription(reqentity.getDescription());
        }
        if (reqentity.getPhoto() != null) {
            try {
                events.updateEventImage(event, reqentity.getPhoto());
            }
            catch (Exception ex) {
                Log.warning(TAG, "*** Event image could not be updated, reason: " + ex.getLocalizedMessage());
            }
        }
        if (reqentity.getEventStart() > 0L) {
            event.setEventStart(reqentity.getEventStart());
        }
        event.setIsPublic(reqentity.getIsPublic());
        event.setRepeatWeekDays(reqentity.getRepeatWeekDays());
        event.setRepeatDayTime(reqentity.getRepeatDayTime());
        event.setVotingTimeBegin(reqentity.getVotingTimeBegin());

        try {
            events.updateEvent(event);
        }
        catch (Exception ex) {
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to update event.", ResponseResults.CODE_INTERNAL_SRV_ERROR, jsonresponse.build().toString());
        }

        // notify all event members about its change
        EventNotifications notifications = new EventNotifications(notifyUsersEvent, notifyUserRelativesEvent);
        notifications.sendNotifyEventChanged(EventNotifications.ChangeType.Modify, AuthorityConfig.getInstance().getSessionUser(request), event);

        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Event successfully updated", ResponseResults.CODE_OK, jsonresponse.build().toString());
    }

    /**
     * Delete an event with given ID. The event will be marked as deleted, so it can be
     * purged later.
     * 
     * @param id        Event ID
     * @param request   HTTP request
     * @return          JSON response
     */
    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String remove(@PathParam("id") Long id, @Context HttpServletRequest request) {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);
        EventEntity event = entities.find(EventEntity.class, id);
        jsonresponse.add("id", id.toString());
        if (event == null) {
            Log.warning(TAG, "*** User was attempting to delete non-existing event!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to find user for deletion.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        // check if the event owner or a user with higher privilege is trying to remove the event
        if (!users.userIsOwnerOrAdmin(sessionuser, event.getStatus())) {
            Log.warning(TAG, "*** User was attempting to remove an event without proper privilege!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to remove event, insufficient privilege.", ResponseResults.CODE_FORBIDDEN, jsonresponse.build().toString());
        }

        // notify all event members about its removal, this must happen before we mark the event as deleted!
        EventNotifications notifications = new EventNotifications(notifyUsersEvent, notifyUserRelativesEvent);
        notifications.sendNotifyEventChanged(EventNotifications.ChangeType.Remove, AuthorityConfig.getInstance().getSessionUser(request), event);

        try {
            events.markEventAsDeleted(event);
        }
        catch (Exception ex) {
            Log.warning(TAG, "*** Could not mark event as deleted, reason: " + ex.getLocalizedMessage());
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to delete event.", ResponseResults.CODE_INTERNAL_SRV_ERROR, jsonresponse.build().toString());
        }

        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Event successfully deleted", ResponseResults.CODE_OK, jsonresponse.build().toString());
    }

    /**
     * Find an event with given ID.
     * 
     * @param id        Event ID
     * @param request   HTTP request
     * @return          JSON response
     */
    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String find(@PathParam("id") Long id, @Context HttpServletRequest request) {
        EventEntity event = entities.find(EventEntity.class, id);
        if ((event == null) || !event.getStatus().getIsActive()) {
            JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
            jsonresponse.add("id", id.toString());
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Event was not found.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);
        JsonObjectBuilder exportedevent = events.exportUserEventJSON(event, sessionuser, connections);
        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Event was found.", ResponseResults.CODE_OK, exportedevent.build().toString());
    }

    /**
     * Get all events.
     * 
     * @param request       HTTP request
     * @return              JSON response
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String findAllEvents(@Context HttpServletRequest request) {
        List<EventEntity> foundevents = entities.findAll(EventEntity.class);
        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);
        JsonArrayBuilder exportedevents = events.exportUserEventsJSON(foundevents, sessionuser, connections);
        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "List of events", ResponseResults.CODE_OK, exportedevents.build().toString());
    }

    /**
     * Get events in given range.
     * 
     * @param from          Range begin
     * @param to            Range end
     * @param request       HTTP request
     * @return              JSON response
     */
    @GET
    @Path("{from}/{to}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String findRange(@PathParam("from") Integer from, @PathParam("to") Integer to, @Context HttpServletRequest request) {
        List<EventEntity> foundevents = entities.findRange(EventEntity.class, from, to);
        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);
        JsonArrayBuilder exportedevents = events.exportUserEventsJSON(foundevents, sessionuser, connections);
        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "List of events", ResponseResults.CODE_OK, exportedevents.build().toString());
    }

    /**
     * Get the total count of events.
     * 
     * @return JSON response
     */
    @GET
    @Path("count")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String countREST() {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        // NOTE the final event count is the count of EventEntity entries in database minus the count of events to be purged
        AppInfoEntity appinfo = appInfos.getAppInfoEntity();
        Long eventpurges = appinfo.getEventCountPurge();
        jsonresponse.add("count", entities.getCount(EventEntity.class) - eventpurges);
        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Count of events", ResponseResults.CODE_OK, jsonresponse.build().toString());
    }

    /**
     * Add a member to given event.
     * 
     * @param eventId      Event ID
     * @param memberId     Member ID
     * @param request      HTTP request
     * @return             JSON response
     */
    @PUT
    @Path("addmember/{eventId}/{memberId}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String addMember(@PathParam("eventId") Long eventId, @PathParam("memberId") Long memberId, @Context HttpServletRequest request) {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        if ((eventId == null) || (memberId == null)) {
            Log.error(TAG, "*** Cannot add member to event, no valid inputs!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to add member to event, invalid input.", ResponseResults.CODE_NOT_ACCEPTABLE, jsonresponse.build().toString());
        }

        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);

        jsonresponse.add("eventId", eventId.toString());
        jsonresponse.add("memberId", memberId.toString());

        // check if both, member and event exist
        UserEntity  user2add = users.findUser(memberId);
        EventEntity event = entities.find(EventEntity.class, eventId);
        if ((user2add == null) || !user2add.getStatus().getIsActive()) {
            user2add = null;
        }
        if ((event == null) || !event.getStatus().getIsActive()) {
            event = null;
        }
        if ((event == null) || (user2add == null)) {
            Log.warning(TAG, "*** Cannot add member to event: non-existing member or event!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to add member to event.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        // check if the event owner or a user with higher privilege is trying to modify the event
        if (!users.userIsOwnerOrAdmin(sessionuser, event.getStatus())) {
            Log.warning(TAG, "*** User was attempting to modify (add member) an event without proper privilege!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to add member to event, insufficient privilege.", ResponseResults.CODE_FORBIDDEN, jsonresponse.build().toString());
        }

        try {
            events.addMember(event, user2add);
        }
        catch (Exception ex) {
            Log.warning(TAG, "*** Could not add member to event, reason: " + ex.getLocalizedMessage());
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to add member to event. Reason: " + ex.getLocalizedMessage(), ResponseResults.CODE_INTERNAL_SRV_ERROR, jsonresponse.build().toString());
        }

        // notify all event members about a new member
        EventNotifications notifications = new EventNotifications(notifyUsersEvent, notifyUserRelativesEvent);
        notifications.sendNotifyMemberChanged(EventNotifications.ChangeType.Add, AuthorityConfig.getInstance().getSessionUser(request), event, memberId);

        events.createEventJoiningMail(event, user2add);

        jsonresponse.add("memberName", user2add.getName());
        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Member was added to event.", ResponseResults.CODE_OK, jsonresponse.build().toString());
    }

    /**
     * Remove a member from given event.
     * 
     * @param eventId      Event ID
     * @param memberId     Member ID
     * @param request      HTTP request
     * @return             JSON response
     */
    @PUT
    @Path("removemember/{eventId}/{memberId}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String removeMember(@PathParam("eventId") Long eventId, @PathParam("memberId") Long memberId, @Context HttpServletRequest request) {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        if ((eventId == null) || (memberId == null)) {
            Log.error(TAG, "*** Cannot remove member from event, no valid inputs!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to remove member from event, invalid input.", ResponseResults.CODE_NOT_ACCEPTABLE, jsonresponse.build().toString());
        }

        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);

        jsonresponse.add("eventId", eventId.toString());
        jsonresponse.add("memberId", memberId.toString());

        // check if both, member and event exist
        UserEntity  user2remove = users.findUser(memberId);
        EventEntity event = entities.find(EventEntity.class, eventId);
        if ((user2remove == null) || !user2remove.getStatus().getIsActive()) {
            user2remove = null;
        }
        if ((event == null) || !event.getStatus().getIsActive()) {
            event = null;
        }
        if ((event == null) || (user2remove == null)) {
            Log.warning(TAG, "*** Cannot remove member from event: non-existing member or event!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to remove member from event.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        // check if the member himself, event owner, or a user with higher privilege is trying to modify the event
        if ((!Objects.equals(memberId, sessionuser.getId())) && !users.userIsOwnerOrAdmin(sessionuser, event.getStatus())) {
            Log.warning(TAG, "*** User was attempting to modify (remove member) an event without proper privilege!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to remove member from event, insufficient privilege.", ResponseResults.CODE_FORBIDDEN, jsonresponse.build().toString());
        }

        try {
            events.removeMember(event, user2remove);
        }
        catch (Exception ex) {
            Log.warning(TAG, "*** Could not remove member from event, reason: " + ex.getLocalizedMessage());
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to remove member from event. Reason: " + ex.getLocalizedMessage(), ResponseResults.CODE_INTERNAL_SRV_ERROR, jsonresponse.build().toString());
        }

        // notify all event members about removing a member
        EventNotifications notifications = new EventNotifications(notifyUsersEvent, notifyUserRelativesEvent);
        notifications.sendNotifyMemberChanged(EventNotifications.ChangeType.Remove, AuthorityConfig.getInstance().getSessionUser(request), event, memberId);

        events.createEventLeavingMail(event, user2remove);

        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Member was removed from event.", ResponseResults.CODE_OK, jsonresponse.build().toString());
    }

    /**
     * Notify all event members. This service can be used e.g. for buzzing all members.
     * 
     * @param eventId           Event ID
     * @param notificationJson  Notification content
     * @param request           HTTP request
     * @return                  JSON response
     */
    @POST
    @Path("notifyMembers/{eventId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String notifyMembers(@PathParam("eventId") Long eventId, String notificationJson, @Context HttpServletRequest request) {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        if (eventId == null) {
            Log.error(TAG, "*** Cannot notify event members, no valid inputs!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to notify event members, invalid input.", ResponseResults.CODE_NOT_ACCEPTABLE, jsonresponse.build().toString());
        }

        jsonresponse.add("eventId", eventId.toString());

        EventEntity event = entities.find(EventEntity.class, eventId);
        if ((event == null) || !event.getStatus().getIsActive()) {
            Log.warning(TAG, "*** Cannot notify event members: non-existing event!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed notify event members, invalid event.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        JsonReader jreader = Json.createReader(new StringReader(notificationJson));
        JsonObject jobject = jreader.readObject();

        EventNotifications notifications = new EventNotifications(notifyUsersEvent, notifyUserRelativesEvent);
        notifications.notifyEventMembers(AuthorityConfig.getInstance().getSessionUser(request), event, jobject);

        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Event members were notified.", ResponseResults.CODE_OK, jsonresponse.build().toString());
    }

    /**
     * Get the location with given ID.
     * 
     * @param eventId      Event ID
     * @param locationId   Location ID
     * @param request      HTTP request
     * @return             JSON response
     */
    @GET
    @Path("location/{eventId}/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String getLocation(@PathParam("eventId") Long eventId, @PathParam("locationId") Long locationId, @Context HttpServletRequest request) {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        if ((eventId == null) || (locationId == null)) {
            Log.error(TAG, "*** Cannot get event location, no valid inputs!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to get event location, invalid input.", ResponseResults.CODE_NOT_ACCEPTABLE, jsonresponse.build().toString());
        }

        EventEntity event = events.findEvent(eventId);
        if (event == null) {
            Log.warning(TAG, "*** Cannot get location: non-existing event!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to get event location. Event does not exist.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);
        // check if the event owner or a user with higher privilege is trying to modify the event locations
        if (!event.getIsPublic() && !users.userIsOwnerOrAdmin(sessionuser, event.getStatus())) {
            Log.warning(TAG, "*** User was attempting to get event information without proper privilege!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to get event location, insufficient privilege.", ResponseResults.CODE_FORBIDDEN, jsonresponse.build().toString());
        }

        EventLocationEntity location = eventLocations.findLocation(locationId);
        if ((location == null) || !location.getStatus().getIsActive()) {
            Log.warning(TAG, "*** Failed to get event location, it does not exist!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to get event location. Location does not exist.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        JsonObjectBuilder exportedlocation = eventLocations.exportEventLocationJSON(location);
        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Location was successfully added/update.", ResponseResults.CODE_OK, exportedlocation.build().toString());
    }

    /**
     * Add a new or update an existing location. If the input has an id field, then
     * an update attempt for that location entity with given ID is performed. If no id
     * field exists, then a new location entity is created and added to given event.
     * 
     * @param eventId      Event ID
     * @param locationJson Location to add in JSON format
     * @param request      HTTP request
     * @return             JSON response
     */
    @PUT
    @Path("putlocation/{eventId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String putLocation(@PathParam("eventId") Long eventId, String locationJson, @Context HttpServletRequest request) {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        if ((eventId == null) || (locationJson == null)) {
            Log.error(TAG, "*** Cannot add location to event, no valid inputs!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to add/update event location, invalid input.", ResponseResults.CODE_NOT_ACCEPTABLE, jsonresponse.build().toString());
        }

        EventEntity event = events.findEvent(eventId);
        if (event == null) {
            Log.warning(TAG, "*** Cannot add location to event: non-existing event!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to add/update member from event.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);
        // check if the event owner or a user with higher privilege is trying to modify the event locations
        if (!users.userIsOwnerOrAdmin(sessionuser, event.getStatus())) {
            Log.warning(TAG, "*** User was attempting to update an event without proper privilege!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to add/update location to event, insufficient privilege.", ResponseResults.CODE_FORBIDDEN, jsonresponse.build().toString());
        }

        EventLocationEntity inputlocation;
        try {
            inputlocation = validator.validateLocationInput(locationJson, event);
        }
        catch (Exception ex) {
            Log.warning(TAG, "*** Could not add location, validation failed, reason: " + ex.getLocalizedMessage());
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, ex.getLocalizedMessage(), ResponseResults.CODE_BAD_REQUEST, jsonresponse.build().toString());
        }

        EventNotifications.ChangeType changetype;
        EventLocationEntity location;
        try {
            // add new location or update an existing one?
            if ((inputlocation.getId() != null) && (inputlocation.getId() > 0)) {
                location = eventLocations.updateLocation(inputlocation);   
                changetype = EventNotifications.ChangeType.Add;
            }
            else {
                if (!validator.validateUniqueLocationName(event, inputlocation.getName())) {
                    throw new Exception("There is already a location with this name.");
                }
                location = eventLocations.createNewLocation(event, inputlocation, sessionuser.getId());
                changetype = EventNotifications.ChangeType.Modify;
            }
        }
        catch (Exception ex) {
            Log.warning(TAG, "*** Could not add/update location, reaon: " + ex.getLocalizedMessage());
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to add/update location. " + ex.getLocalizedMessage(), ResponseResults.CODE_INTERNAL_SRV_ERROR, jsonresponse.build().toString());
        }

        // notify all event members about the location change
        EventNotifications notifications = new EventNotifications(notifyUsersEvent, notifyUserRelativesEvent);
        notifications.sendNotifyLocationChanged(changetype, AuthorityConfig.getInstance().getSessionUser(request), event, location.getId());

        //! NOTE on successful entity location creation the new ID is sent back by results.data field.
        jsonresponse.add("eventId", event.getId().toString());
        jsonresponse.add("locationId", location.getId().toString());
        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Location was successfully added/update.", ResponseResults.CODE_OK, jsonresponse.build().toString());
    }

    /**
     * Remove a location from given event.
     * 
     * @param eventId      Event ID
     * @param locationId   Location ID
     * @param request      HTTP request
     * @return             JSON response
     */
    @POST
    @Path("removelocation/{eventId}/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles={AuthRole.VIRT_ROLE_USER})
    public String removeLocation(@PathParam("eventId") Long eventId, @PathParam("locationId") Long locationId, @Context HttpServletRequest request) {
        JsonObjectBuilder jsonresponse = Json.createObjectBuilder();
        if ((eventId == null) || (locationId == null)) {
            Log.error(TAG, "*** Cannot remove location from event, no valid inputs!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to remove location from event, invalid input.", ResponseResults.CODE_NOT_ACCEPTABLE, jsonresponse.build().toString());
        }

        UserEntity sessionuser = AuthorityConfig.getInstance().getSessionUser(request);

        jsonresponse.add("eventId", eventId.toString());
        jsonresponse.add("locationId", locationId.toString());

        // check if both, member and event exist
        EventLocationEntity loc2remove = eventLocations.findLocation(locationId);
        EventEntity event = entities.find(EventEntity.class, eventId);

        if ((loc2remove == null) || !loc2remove.getStatus().getIsActive()) {
            loc2remove = null;
        }
        if ((event == null) || !event.getStatus().getIsActive()) {
            event = null;
        }
        if ((event == null) || (loc2remove == null)) {
            Log.warning(TAG, "*** Cannot remove location from event: non-existing location or event!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to remove location from event. Event or location does not exist.", ResponseResults.CODE_NOT_FOUND, jsonresponse.build().toString());
        }

        // check if the event owner or a user with higher privilege is trying to modify the event
        if (!users.userIsOwnerOrAdmin(sessionuser, event.getStatus())) {
            Log.warning(TAG, "*** User was attempting to modify (remove location) an event without proper privilege!");
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to remove location from event, insufficient privilege.", ResponseResults.CODE_FORBIDDEN, jsonresponse.build().toString());
        }

        try {
            eventLocations.markLocationAsDeleted(event, loc2remove);
        }
        catch (Exception ex) {
            Log.warning(TAG, "*** Could not remove location from event, reason: " + ex.getLocalizedMessage());
            return ResponseResults.toJSON(ResponseResults.STATUS_NOT_OK, "Failed to remove location from event. Reason: " + ex.getLocalizedMessage(), ResponseResults.CODE_INTERNAL_SRV_ERROR, jsonresponse.build().toString());
        }

        // notify all event members about removing a location
        EventNotifications notifications = new EventNotifications(notifyUsersEvent, notifyUserRelativesEvent);
        notifications.sendNotifyLocationChanged(EventNotifications.ChangeType.Remove, AuthorityConfig.getInstance().getSessionUser(request), event, locationId);

        return ResponseResults.toJSON(ResponseResults.STATUS_OK, "Location was succssfully removed from event.", ResponseResults.CODE_OK, jsonresponse.build().toString());
    }
}
