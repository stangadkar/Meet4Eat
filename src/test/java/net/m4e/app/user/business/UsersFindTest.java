/*
 * Copyright (c) 2017-2019 by Botorabi. All rights reserved.
 * https://github.com/botorabi/Meet4Eat
 *
 * License: MIT License (MIT), read the LICENSE text in
 *          main directory for more details.
 */
package net.m4e.app.user.business;

import net.m4e.common.UserEntityCreator;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.*;

/**
 * @author boto
 * Date of creation February 6, 2018
 */
class UsersFindTest extends UsersTestBase {

    UserEntity userByID;
    UserEntity userByLogin;
    UserEntity userByEmail;

    Long ANY_ID = 1L;
    String LOGIN_GOOD = "goodlogin";
    String LOGIN_BAD = "badlogin";
    String EMAIL_GOOD = "goodemail";
    String EMAIL_BAD = "bademail";

    @Nested
    class Find {

        @BeforeEach
        void setUp() {

            userByID = UserEntityCreator.create();
            Mockito.when(entities.find(any(), anyLong())).thenReturn(userByID);

            userByLogin = UserEntityCreator.create();
            userByLogin.setLogin("USER_LOGIN");
            Mockito.when(entities.findByField(any(), eq("login"), eq(LOGIN_GOOD))).thenReturn(Arrays.asList(userByLogin));
            Mockito.when(entities.findByField(any(), eq("login"), eq(LOGIN_BAD))).thenReturn(Arrays.asList(userByLogin, userByLogin));

            userByEmail = UserEntityCreator.create();
            userByEmail.setEmail("USER_EMAIL");
            Mockito.when(entities.findByField(any(), eq("email"), eq(EMAIL_GOOD))).thenReturn(Arrays.asList(userByLogin));
            Mockito.when(entities.findByField(any(), eq("email"), eq(EMAIL_BAD))).thenReturn(Arrays.asList(userByLogin, userByLogin));
        }

        @Test
        void findById() {
            assertThat(users.findUser(ANY_ID)).isEqualTo(userByID);
        }

        @Test
        void findByLogin() {
            assertThat(users.findUser(LOGIN_GOOD)).isEqualTo(userByLogin);
        }

        @Test
        void findByLoginInternalError() {
            assertThat(users.findUser(LOGIN_BAD)).isEqualTo(null);
        }

        @Test
        void findByEmail() {
            assertThat(users.findUserByEmail(EMAIL_GOOD)).isEqualTo(userByEmail);
        }

        @Test
        void findByEmailInternalError() {
            assertThat(users.findUserByEmail(EMAIL_BAD)).isEqualTo(null);
        }
    }
}
