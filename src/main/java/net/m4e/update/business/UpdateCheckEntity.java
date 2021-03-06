/*
 * Copyright (c) 2017-2019 by Botorabi. All rights reserved.
 * https://github.com/botorabi/Meet4Eat
 *
 * License: MIT License (MIT), read the LICENSE text in
 *          main directory for more details.
 */
package net.m4e.update.business;

import net.m4e.common.EntityBase;

import javax.json.bind.annotation.*;
import javax.persistence.*;
import java.io.Serializable;

/**
 * Entity for holding update check information. This entity can be used
 * for notifying about availability of a client update.
 * 
 * @author boto
 * Date of creation Dec 5, 2017
 */
@Entity
@NamedQueries({
    /**
     * Try to find an update entry given the client name, platform, and version.
     * Query parameters:
     * 
     * name     Application (client) name
     * platform Platform such as MSWin, MacOS, Linux
     * version  Client's current version
     */
    @NamedQuery(
      name = "UpdateCheckEntity.findUpdate",
      query = "SELECT u FROM UpdateCheckEntity u WHERE u.name = :name AND u.os = :os ORDER BY u.releaseDate DESC"
    ),
    /**
     * Try to find an update entry for a flavor given the client name, platform, and version.
     * Query parameters:
     * 
     * flavor   Application flavor such as "Beta-Test", or "Release"
     * name     Application (client) name
     * platform Platform such as MSWin, MacOS, Linux
     * version  Client's current version
     */
    @NamedQuery(
      name = "UpdateCheckEntity.findFlavorUpdate",
      query = "SELECT u FROM UpdateCheckEntity u WHERE u.flavor = :flavor AND u.name = :name AND u.os = :os ORDER BY u.releaseDate DESC"
    )
})
public class UpdateCheckEntity extends EntityBase implements Serializable {

    /**
     * Serialization version
     */
    private static final long serialVersionUID = 1L;

    /**
     * Unique entity ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /**
     * The application name, e.g. client name.
     */
    private String name = "";

    /**
     * Operating system can be MSWin, MacOS, Linux, etc.
     */
    private String os = "";

    /**
     * Application flavor
     */
    private String flavor = "";

    /**
     * Available update version
     */
    private String version = "";

    /**
     * Update release date in seconds since epoch
     */
    private Long releaseDate = 0L;

    /**
     * URL for obtaining the update
     */
    private String url = "";

    /**
     * Is the update active? An update can get deactivated at any time.
     */
    private boolean active = true;

    /**
     * Get the entity ID.
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * Set the entity ID.
     */
    @Override
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Get the application name.
     */
    public String getName() {
        return name;
    }

    /**
     * Set the application name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the operating system such as MSWin, MacOS, Linux
     */
    public String getOs() {
        return os;
    }

    /**
     * Set the operating system.
     */
    public void setOs(String os) {
        this.os = os;
    }

    /**
     * Get the application flavor. It can be used to distribute updates to a 
     * dedicated circle such as beta-testers.
     */
    public String getFlavor() {
        return flavor;
    }

    /**
     * Set the application flavor.
     */
    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    /**
     * Get the update  version.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set the update version.
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Get the release date of update.
     */
    public Long getReleaseDate() {
        return releaseDate;
    }

    /**
     * Set the update release date.
     */
    public void setReleaseDate(Long releaseDate) {
        this.releaseDate = releaseDate;
    }

    /**
     * Get the URL to grab the update.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Set the URL to grab the update.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Is the update active?
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Activate/deactivate the update.
     */
    public void setActive(boolean active) {
        this.active = active;
    }
}
