/*
 * Copyright (c) 2017-2018 by Botorabi. All rights reserved.
 * https://github.com/botorabi/Meet4Eat
 * 
 * License: MIT License (MIT), read the LICENSE text in
 *          main directory for more details.
 */

package net.m4e.app.mailbox;

import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.time.Instant;

/**
 * This class represents a mail. It is composed by several chunks of information
 * provided by MailEntity and MailUserEntity.
 * 
 * @author boto
 * Date of creation Nov 3, 2017
 */
public class Mail {

    /**
     * The mail entity
     */
    private MailEntity mailEntity;

    /**
     * The 'unread' state of the mail. Once the mail is read by user this flag will be set to false.
     */
    private boolean unread;

    /**
     * The timestamp of 'trashing' the mail, i.e. the mail is marked as 'free to delete'.
     */
    private Instant trashDate;

    /**
     * Create a mail instance.
     * 
     * @param mailEntity    The mail entity
     * @param unread        Unread flag
     * @param trashDate     If trashed then the trash date, otherwise 0
     */
    public Mail(
        MailEntity mailEntity,
        boolean unread,
        Instant trashDate
    ) {
        this.mailEntity = mailEntity;
        this.unread = unread;
        this.trashDate = trashDate;
    }

    /**
     * Get the mail entity. This entity contains the mail content.
     * 
     * @return The mail entity
     */
    public MailEntity getMailEntity() {
        return mailEntity;
    }

    /**
     * Set the mail entity.
     * 
     * @param mailEntity The mail entity
     */
    public void setMailEntity(MailEntity mailEntity) {
        this.mailEntity = mailEntity;
    }

    /**
     * Is the mail marked as trash?
     * 
     * @return Return true if the mail is marked as trash
     */
    public boolean isTrashed() {
        return (trashDate == null) ? false : (trashDate.getEpochSecond() != 0);
    }

    /**
     * Get the trash date in milliseconds since epoch.
     * 
     * @return Return the timestamp of trashing the mail
     */
    public Instant getTrashDate() {
        return trashDate;
    }

    /**
     * Mark the mail as trash by setting a trash timestamp. Trashed mails will get
     * deleted automatically after a period of time.
     * 
     * @param trashDate Date of trashing the mail
     */
    public void setTrashDate(Instant trashDate) {
        this.trashDate = trashDate;
    }

    /**
     * Is the mail in state 'unread'?
     * 
     * @return 'unread' state
     */
    public boolean isUnread() {
        return unread;
    }

    /**
     * Set the 'unread' state.
     * 
     * @param unread Pass false once the mail was read
     */
    public void setUnread(boolean unread) {
        this.unread = unread;
    }
}
