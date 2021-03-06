/*
 * Copyright (c) 2017-2019 by Botorabi. All rights reserved.
 * https://github.com/botorabi/Meet4Eat
 *
 * License: MIT License (MIT), read the LICENSE text in
 *          main directory for more details.
 */
package net.m4e.tests;

import net.m4e.common.GenericResponseResult;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.util.Objects;

/**
 * @author ybroeker
 */
public class ResponseAssert<T> extends AbstractObjectAssert<ResponseAssert<T>, GenericResponseResult<T>> {

    public ResponseAssert(final GenericResponseResult<T> tGenericResponseResult) {
        super(tGenericResponseResult, ResponseAssert.class);
    }

    //Status
    public ResponseAssert<T> hasStatusOk() {
        String failMessage = "\nExpecting status of:\n  <%s>\nto be:\n  OK\nbut was:\n  Not Ok";
        if (!Objects.areEqual(actual.getStatus(), GenericResponseResult.STATUS_OK)) {
            failWithMessage(failMessage, actual);
        }
        return this;
    }

    public ResponseAssert<T> hasStatusNotOk() {
        String failMessage = "\nExpecting status of:\n  <%s>\nto be:\n  Not OK\nbut was:\n  Ok";
        if (!Objects.areEqual(actual.getStatus(), GenericResponseResult.STATUS_NOT_OK)) {
            failWithMessage(failMessage, actual);
        }
        return this;
    }

    //Description
    public ResponseAssert<T> hasDescription() {
        isNotNull();
        String failMessage = "\nExpecting:\n  <%s>\nto have non-empty description\nbut was:\n  <%s>";
        if (actual.getDescription() == null) {
            failWithMessage(failMessage, actual, null);
        } else if (actual.getDescription().isEmpty()) {
            failWithMessage(failMessage, actual, "");
        }
        return this;
    }

    public ResponseAssert<T> hasDescription(String description) {
        isNotNull();
        String failMessage = "\nExpecting description of:\n  <%s>\nto be:\n  <%s>\nbut was:\n  <%s>";
        if (!Objects.areEqual(actual.getDescription(), description)) {
            failWithMessage(failMessage, actual, description, actual.getDescription());
        }
        return this;
    }

    //Data
    public ResponseAssert<T> hasData() {
        isNotNull();
        String failMessage = "\nExpecting:\n  <%s>\nto have data\nbut was:\n  <null>";
        if (actual.getData() == null) {
            failWithMessage(failMessage, actual, null);
        }
        return this;
    }

    public ResponseAssert<T> hasNoData() {
        isNotNull();
        String failMessage = "\nExpecting:\n  <%s>\nto have no data\nbut was:\n  <%s>";
        if (actual.getData() != null) {
            failWithMessage(failMessage, actual, actual.getData());
        }
        return this;
    }

    public ResponseAssert<T> hasData(Object data) {
        isNotNull();
        String failMessage = "\nExpecting data of:\n  <%s>\nto be:\n  <%s>\nbut was:\n  <%s>";
        if (!Objects.areEqual(actual.getData(), data)) {
            failWithMessage(failMessage, actual, data, actual.getData());
        }
        return this;
    }

    public ResponseAssert<T> hasDataOfType(Class clazz) {
        isNotNull();

        String failMessage = "\nExpecting data of:\n  <%s>\nto be of type:\n  <%s>\nbut was:\n  <%s>";
        if (!clazz.isInstance(actual.getData())) {
            failWithMessage(failMessage, actual, clazz, actual.getData().getClass());
        }
        return this;
    }


    //Status-Code-Ranges
    public ResponseAssert<T> isInformation() {
        String failMessage = "\nExpecting status code of:\n  <%s>\nto be:\n  Information (1xx)\nbut was:\n  <%s>";
        if (!(actual.getCode() >= 100 && actual.getCode() < 199)) {
            failWithMessage(failMessage, actual, String.valueOf(actual.getCode()));
        }
        return this;
    }

    public ResponseAssert<T> isSuccessful() {
        String failMessage = "\nExpecting status code of:\n  <%s>\nto be:\n  Successful (2xx)\nbut was:\n  <%s>";
        if (!(actual.getCode() >= 200 && actual.getCode() < 299)) {
            failWithMessage(failMessage, actual, String.valueOf(actual.getCode()));
        }
        return this;
    }

    public ResponseAssert<T> isRedirect() {
        String failMessage = "\nExpecting status code of:\n  <%s>\nto be:\n  Redirect (3xx)\nbut was:\n  <%s>";
        if (!(actual.getCode() >= 300 && actual.getCode() < 399)) {
            failWithMessage(failMessage, actual, String.valueOf(actual.getCode()));
        }
        return this;
    }

    public ResponseAssert<T> isClientError() {
        String failMessage = "\nExpecting status code of:\n  <%s>\nto be:\n  Client Error (4xx)\nbut was:\n  <%s>";
        if (!(actual.getCode() >= 400 && actual.getCode() < 499)) {
            failWithMessage(failMessage, actual, String.valueOf(actual.getCode()));
        }
        return this;
    }

    public ResponseAssert<T> isServerError() {
        String failMessage = "\nExpecting status code of:\n  <%s>\nto be:\n  Server Error (5xx)\nbut was:\n  <%s>";
        if (!(actual.getCode() >= 500 && actual.getCode() < 599)) {
            failWithMessage(failMessage, actual, String.valueOf(actual.getCode()));
        }
        return this;
    }

    //Status-Codes

    public ResponseAssert<T> isStatusCode(int code) {
        isNotNull();
        String failMessage = "\nExpecting status code of:\n  <%s>\nto be:\n  <%s>\nbut was:\n  <%s>";
        if (!(actual.getCode() == code)) {
            failWithMessage(failMessage, actual, String.valueOf(code), String.valueOf(actual.getCode()));
        }
        return this;
    }

    public ResponseAssert<T> codeIsUnauthorized() {
        return isStatusCode(GenericResponseResult.CODE_UNAUTHORIZED);
    }

    public ResponseAssert<T> codeIsOk() {
        return isStatusCode(GenericResponseResult.CODE_OK);
    }

    public ResponseAssert<T> codeIsNotFound() {
        return isStatusCode(GenericResponseResult.CODE_NOT_FOUND);
    }

    public ResponseAssert<T> codeIsNotAcceptable() {
        return isStatusCode(GenericResponseResult.CODE_NOT_ACCEPTABLE);
    }

    public ResponseAssert<T> codeIsBadRequest() {
        return isStatusCode(GenericResponseResult.CODE_BAD_REQUEST);
    }

    public ResponseAssert<T> codeIsForbidden() {
        return isStatusCode(GenericResponseResult.CODE_FORBIDDEN);
    }

    public ResponseAssert<T> codeIsInternalError() {
        return isStatusCode(GenericResponseResult.CODE_INTERNAL_SRV_ERROR);
    }
}
