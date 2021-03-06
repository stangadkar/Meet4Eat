/*
 * Copyright (c) 2017-2019 by Botorabi. All rights reserved.
 * https://github.com/botorabi/Meet4Eat
 *
 * License: MIT License (MIT), read the LICENSE text in
 *          main directory for more details.
 */
package net.m4e.app.user.rest;

import io.swagger.annotations.*;
import net.m4e.app.auth.*;
import net.m4e.app.communication.ConnectedClients;
import net.m4e.app.user.business.*;
import net.m4e.app.user.rest.comm.*;
import net.m4e.common.*;
import net.m4e.system.core.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.*;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.lang.invoke.MethodHandles;
import java.util.*;

/**
 * REST services for User entity operations
 *
 * @author boto
 * Date of creation Aug 18, 2017
 */
@Stateless
@Path("/rest/users")
@Api(value = "User service")
public class UserRestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Entities entities;

    private final Users users;

    private final UserValidator validator;

    private final UserRegistrations registration;

    private final AppInfos appInfos;

    private final ConnectedClients connections;


    /**
     * Make the EJB container happy.
     */
    protected UserRestService() {
        users = null;
        entities = null;
        validator = null;
        registration = null;
        appInfos = null;
        connections = null;
    }

    /**
     * Create the user service.
     */
    @Inject
    public UserRestService(@NotNull Users users,
                           @NotNull Entities entities,
                           @NotNull UserValidator validator,
                           @NotNull UserRegistrations registration,
                           @NotNull AppInfos appInfos,
                           @NotNull ConnectedClients connections) {

        this.users = users;
        this.entities = entities;
        this.validator = validator;
        this.registration = registration;
        this.appInfos = appInfos;
        this.connections = connections;
    }

    /**
     * Create a new user.
     */
    @POST
    @Path("create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.USER_ROLE_ADMIN})
    @ApiOperation(value = "Create a new user")
    public GenericResponseResult<UserId> createUser(UserCmd userCmd, @Context HttpServletRequest request) {
        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);

        UserEntity userEntity;
        try {
            userEntity = validator.validateNewEntityInput(sessionUser, userCmd);
        } catch (Exception ex) {
            LOGGER.warn("*** Could not create new user, validation failed, reason: {}", ex.getMessage());
            return GenericResponseResult.badRequest(ex.getMessage());
        }

        UserEntity createdUser = users.createNewUser(userEntity, sessionUser.getId());

        return GenericResponseResult.ok("User was successfully created.", new UserId(createdUser.getId().toString()));
    }

    /**
     * Register a new user. For activating the user, there is an activation process.
     * Only guests can use this service.
     */
    @POST
    @Path("register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_GUEST})
    @ApiOperation(value = "Register a new user")
    public GenericResponseResult<UserId> registerUser(UserCmd userCmd, @Context HttpServletRequest request) {
        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);
        if (sessionUser != null) {
            LOGGER.error("*** an already authenticated user tries a user registration!");
            return GenericResponseResult.notAcceptable("Failed to register user, logout first.");
        }

        UserEntity userEntity;
        try {
            userEntity = validator.validateNewEntityInput(sessionUser, userCmd);
        } catch (Exception ex) {
            LOGGER.warn("*** Could not register a new user, validation failed, reason: {}", ex.getMessage());
            return GenericResponseResult.badRequest(ex.getLocalizedMessage());
        }

        // just to be safe: no roles can be defined during registration
        userEntity.setRoles(null);

        UserEntity newEntity;
        try {
            newEntity = users.createNewUser(userEntity, null);
            // the user is not enabled until the registration process was completed
            newEntity.getStatus().setEnabled(false);
        } catch (Exception ex) {
            LOGGER.warn("*** Could not register a new user, reason: {}", ex.getLocalizedMessage());
            return GenericResponseResult.internalError("Failed to register a new user.");
        }

        // get the activation URL
        String activationUrl = getAccRegCfgLinkURL("url.activation", request, "/activate.html");
        String adminEmail = getAccRegCfgNotificationMail();

        registration.registerUserAccount(newEntity, activationUrl, adminEmail);

        LOGGER.info("A new user was successfully registered and waits for account activation: {} ({})",
                newEntity.getName(), newEntity.getEmail());

        //! NOTE on successful entity creation the new ID is sent back by results.data field.
        return GenericResponseResult.ok("User was successfully created.", new UserId(newEntity.getId().toString()));
    }

    /**
     * Get the link URL used in emails. If a valid account configuration file exists in app then the link is retrieved
     * from that file, otherwise the current request URL is used to create a link.
     *
     * @param configName  If a valid account registration config was found in app then this is the config settings name
     * @param request     Used to create an URL basing on current http request if no valid configuration exists in app
     * @param defaultPage Last part of the URL if no valid configuration exists in app
     * @return The requested URL
     */
    private String getAccRegCfgLinkURL(String configName, HttpServletRequest request, String defaultPage) {
        // first try to get the link from account registration config
        Properties props = AppConfiguration.getInstance().getAccountRegistrationConfig();
        String link = (props != null) ? props.getProperty(configName) : null;
        // need to fall back to current server url?
        if (link == null) {
            return AppConfiguration.getInstance().getHTMLBaseURL(request) + defaultPage;
        }
        return link;
    }

    /**
     * Get the notification mail address as configured in account registration configuration file.
     *
     * @return Return the configured notification email address, or null if it is not configured.
     */
    private String getAccRegCfgNotificationMail() {
        Properties props = AppConfiguration.getInstance().getAccountRegistrationConfig();
        String mail = (props != null) ? props.getProperty("mail.notification") : null;
        return mail;
    }

    /**
     * Activate a user by given its activation token. This is usually used during the registration process.
     */
    @GET
    @Path("activate/{token}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_GUEST})
    @ApiOperation(value = "Activate a fresh registered user with given token")
    public GenericResponseResult<ActivateUser> activateUser(@PathParam("token") String token, @Context HttpServletRequest request) {
        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);
        if (sessionUser != null) {
            LOGGER.error("*** an already authenticated user tries a user activation!");
            return GenericResponseResult.notAcceptable("Failed to activate user account, logout first.");
        }

        LOGGER.trace("activating user account, token: " + token);
        UserEntity user;
        try {
            user = registration.activateUserAccount(token);
        } catch (Exception ex) {
            LOGGER.debug("user activation failed, reason: {}", ex.getLocalizedMessage());
            return GenericResponseResult.notAcceptable(
                    "Failed to activate user account! Reason: " + ex.getLocalizedMessage());
        }
        return GenericResponseResult.ok("User was successfully activated.", new ActivateUser(user.getName()));
    }

    /**
     * Request for resetting a user password. Only guests can use this service.
     */
    @POST
    @Path("requestpasswordreset")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_GUEST})
    @ApiOperation(value = "Request for resetting the account password")
    public GenericResponseResult<Void> requestPasswordReset(RequestPasswordResetCmd requestPasswordResetCmd, @Context HttpServletRequest request) {
        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);
        if (sessionUser != null) {
            LOGGER.error("*** an already authenticated user tries to reset the password!");
            return GenericResponseResult.notAcceptable("Failed to reset user password, logout first.");
        }

        try {
            if (requestPasswordResetCmd.getEmail() == null) {
                LOGGER.error("cannot process password reset request, invalid input");
                return GenericResponseResult.notAcceptable("Failed to reset user password, invalid input.");
            }
            // create the activation URL
            String url = getAccRegCfgLinkURL("url.passwordReset", request, "/resetpassword.html");
            String adminEmail = getAccRegCfgNotificationMail();
            registration.requestPasswordReset(requestPasswordResetCmd.getEmail(), url, adminEmail);
        } catch (Exception ex) {
            LOGGER.error("cannot process password reset request, reason: {}", ex.getLocalizedMessage());
            return GenericResponseResult.notAcceptable(
                    "Failed to reset user password! Reason: " + ex.getLocalizedMessage());
        }
        return GenericResponseResult.ok("Request for user password reset was successfully processed.");
    }

    /**
     * Try to set a new password for an user account. The password reset token is validated,
     * on success the user password is reset to the given one in 'performPasswordResetCmd'.
     */
    @POST
    @Path("passwordreset/{token}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_GUEST})
    @ApiOperation(value = "Perform the account password reset with given token")
    public GenericResponseResult<PerformPasswordReset> passwordReset(PerformPasswordResetCmd performPasswordResetCmd, @PathParam("token") String token, @Context HttpServletRequest request) {
        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);
        if (sessionUser != null) {
            LOGGER.error("*** an already authenticated user tries to reset an user password");
            return GenericResponseResult.notAcceptable("Failed to reset user password, logout first.");
        }

        UserEntity user;
        try {
            String newpassword = performPasswordResetCmd.getPassword();
            if (newpassword == null) {
                LOGGER.error("cannot process password reset request, invalid input");
                return GenericResponseResult.notAcceptable("Failed to reset user password, invalid input.");
            }
            user = registration.processPasswordReset(token, newpassword);
        } catch (Exception ex) {
            LOGGER.debug("user password reset failed! Reason: {}", ex.getLocalizedMessage());
            return GenericResponseResult.notAcceptable(ex.getLocalizedMessage());
        }
        return GenericResponseResult.ok("User was successfully activated.", new PerformPasswordReset(user.getName()));
    }

    /**
     * Modify the user with given ID.
     */
    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_USER})
    @ApiOperation(value = "Update an existing user")
    public GenericResponseResult<UserId> edit(@PathParam("id") Long id, UserCmd userCmd, @Context HttpServletRequest request) {
        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);
        UserId updateUser = new UserId(id.toString());
        UserEntity updateEntity;
        try {
            updateEntity = validator.validateUpdateEntityInput(userCmd);
        } catch (Exception ex) {
            return GenericResponseResult.badRequest(
                    "Failed to update user, invalid input. Reason: " + ex.getMessage(), updateUser);
        }

        UserEntity existingUser = entities.find(UserEntity.class, id);
        if ((existingUser == null) || !existingUser.getStatus().getIsActive()) {
            return GenericResponseResult.notFound("Failed to find user for updating.", updateUser);
        }

        // check if a user is updating itself or a user with higher privilege is trying to modify another user
        if (!users.userIsOwnerOrAdmin(sessionUser, existingUser.getStatus())) {
            LOGGER.warn("*** User was attempting to update another user without proper privilege!");
            return GenericResponseResult.forbidden("Failed to update user, insufficient privilege.", updateUser);
        }

        // validate the requested roles, check for roles, e.g. only admins can define admin role for other users
        existingUser.setRoles(users.adaptRequestedRoles(sessionUser, updateEntity.getRoles()));

        // take over non-empty fields
        boolean needsUpdate = false;
        if ((updateEntity.getName() != null) && !updateEntity.getName().isEmpty()) {
            existingUser.setName(updateEntity.getName());
            needsUpdate = true;
        }
        if ((updateEntity.getPassword() != null) && !updateEntity.getPassword().isEmpty()) {
            existingUser.setPassword(updateEntity.getPassword());
            needsUpdate = true;
        }
        if (updateEntity.getPhoto() != null) {
            try {
                users.updateUserImage(existingUser, updateEntity.getPhoto());
                needsUpdate = true;
            } catch (RuntimeException ex) {
                LOGGER.warn("*** User image could not be updated, reason: {}", ex.getMessage());
            }
        }

        if (needsUpdate) {
            try {
                users.updateUser(existingUser);
            } catch (Exception ex) {
                return GenericResponseResult.internalError("Failed to update user.", updateUser);
            }
        } else {
            return GenericResponseResult.badRequest("No input for update.", updateUser);
        }

        return GenericResponseResult.ok("User successfully updated", updateUser);
    }

    /**
     * Delete an user with given ID. The user will be marked as deleted, so it can be
     * purged later.
     */
    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.USER_ROLE_ADMIN})
    @ApiOperation(value = "Request for deleting an existing user")
    public GenericResponseResult<UserId> remove(@PathParam("id") Long id, @Context HttpServletRequest request) {
        UserId response = new UserId(id.toString());
        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);

        if (sessionUser.getId().equals(id)) {
            LOGGER.warn("*** User was attempting to delete itself! Call a doctor.");
            return GenericResponseResult.notAcceptable("Failed to delete yourself.", response);
        }

        UserEntity user = users.findUser(id);
        if ((user == null) || !user.getStatus().getIsActive()) {
            LOGGER.warn("*** User was attempting to delete non-existing user!");
            return GenericResponseResult.notFound("Failed to find user for deletion.", response);
        }

        try {
            users.markUserAsDeleted(user);
        } catch (Exception ex) {
            LOGGER.warn("*** Could not mark user as deleted, reason: {}", ex.getMessage());
            return GenericResponseResult.internalError("Failed to delete user.", response);
        }

        return GenericResponseResult.ok("User successfully deleted", response);
    }

    /**
     * Search for users containing given keyword in their name or email.
     * A maximal of 10 users are returned. The returned list does not contain admins and inactive users.
     *
     * @param keyword Keyword to search for, minimal 6 characters
     * @return Result
     */
    @GET
    @Path("/search/{keyword}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_USER})
    @ApiOperation(value = "Search for given keyword in user names or emails")
    public GenericResponseResult<List<SearchHitUser>> search(@PathParam("keyword") String keyword) {
        if (keyword == null || keyword.isEmpty() || keyword.length() < 6) {
            return GenericResponseResult.ok("Search results", new ArrayList<SearchHitUser>());
        }

        List<String> searchFields = new ArrayList();
        searchFields.add("name");
        if (keyword.contains("@")) {
            searchFields.add("email");
        }

        return GenericResponseResult.ok("Search results", searchForUsers(keyword, searchFields));
    }

    private List<SearchHitUser> searchForUsers(final String keyword, final List<String> searchFields) {
        List<SearchHitUser> searchHits = new ArrayList<>();
        List<UserEntity> hits = entities.searchForString(UserEntity.class, keyword, searchFields, 10);
        for (UserEntity hit : hits) {
            // exclude non-active users and admins from hit list
            if (!hit.getStatus().getIsActive() || users.checkUserRoles(hit, Arrays.asList(AuthRole.USER_ROLE_ADMIN))) {
                continue;
            }
            searchHits.add(new SearchHitUser(
                    hit.getId().toString(),
                    hit.getName(),
                    (hit.getPhoto() != null) ? hit.getPhoto().getId().toString() : "",
                    (hit.getPhoto() != null) ? hit.getPhoto().getETag() : ""));
        }
        return searchHits;
    }

    /**
     * Find an user with given ID.
     */
    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_USER})
    @ApiOperation(value = "Find an user given its ID")
    public GenericResponseResult<UserInfo> find(@PathParam("id") Long id, @Context HttpServletRequest request) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(id.toString());

        UserEntity user = users.findUser(id);
        if ((user == null) || !user.getStatus().getIsActive()) {
            return GenericResponseResult.notFound("User was not found.", userInfo);
        }

        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);
        if (!users.userIsOwnerOrAdmin(sessionUser, user.getStatus())) {
            return GenericResponseResult.unauthorized("Insufficient privilege", userInfo);
        }

        return GenericResponseResult.ok("User was found.", users.exportUser(user, connections));
    }

    /**
     * Get all users.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_USER})
    @ApiOperation(value = "Get all users. An admin gets all users but a non-admin gets only herself/himself.")
    public GenericResponseResult<List<UserInfo>> findAllUsers(@Context HttpServletRequest request) {
        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);
        List<UserEntity> foundUsers = entities.findAll(UserEntity.class);
        List<UserInfo> exportUsers = users.exportUsers(foundUsers, sessionUser, connections);
        return GenericResponseResult.ok("List of users", exportUsers);
    }

    /**
     * Get users in given range.
     */
    @GET
    @Path("{from}/{to}")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_USER})
    @ApiOperation(value = "Get users in given range. An admin gets all users but a non-admin gets only herself/himself.")
    public GenericResponseResult<List<UserInfo>> findRange(@PathParam("from") Integer from, @PathParam("to") Integer to, @Context HttpServletRequest request) {
        UserEntity sessionUser = AuthorityConfig.getInstance().getSessionUser(request);
        List<UserEntity> foundUsers = entities.findRange(UserEntity.class, from, to);
        List<UserInfo> exportUsers = users.exportUsers(foundUsers, sessionUser, connections);
        return GenericResponseResult.ok("List of users", exportUsers);
    }

    /**
     * Get the total count of active users.
     */
    @GET
    @Path("count")
    @Produces(MediaType.APPLICATION_JSON)
    @net.m4e.app.auth.AuthRole(grantRoles = {AuthRole.VIRT_ROLE_USER})
    @ApiOperation(value = "Get the count of active users")
    public GenericResponseResult<UserCount> count() {
        // NOTE the final user count is the count of UserEntity entries in database minus the count of users to be purged
        AppInfoEntity appInfo = appInfos.getAppInfoEntity();
        UserCount count = new UserCount(entities.getCount(UserEntity.class) - appInfo.getUserCountPurge());
        return GenericResponseResult.ok("Count of users", count);
    }
}
