package net.m4e.tests;

import java.io.Serializable;
import java.lang.reflect.Field;

import org.assertj.core.api.AbstractObjectAssert;

/**
 * @author ybroeker
 */
public class EntityAssert<T> extends AbstractObjectAssert<EntityAssert<T>, Class<T>> {

    public EntityAssert(final Class<T> actual) {
        super(actual, EntityAssert.class);
    }

    public EntityAssert<T> isSerializable() {
        String failMessage = "\nExpecting class:\n  <%s>\nto implement:\n  Serializable\nbut doesn't";
        if (!Serializable.class.isAssignableFrom(actual)) {
            failWithMessage(failMessage, actual.getName());
        }
        return this;
    }

    public EntityAssert<T> hasSerialVersionUID() {
        String failMessage = "\nExpecting class:\n  <%s>\nto contain:\n  <static long serialVersionUID>\nbut doesn't";
        try {
            Field field = actual.getDeclaredField("serialVersionUID");
            if (!(field.getType().equals(long.class) && java.lang.reflect.Modifier.isStatic(field.getModifiers()))) {
                failWithMessage(failMessage, actual.getName());
            }
        } catch (NoSuchFieldException e) {
            failWithMessage(failMessage, actual.getName());
        }
        return this;
    }

    public EntityAssert<T> hasEntityAnnotation() {
        String failMessage = "\nExpecting class:\n  <%s>\nto be annotated with:\n  <@Entity>\nbut isn't";
        if (!actual.isAnnotationPresent(javax.persistence.Entity.class)) {
            failWithMessage(failMessage, actual.getName());
        }
        return this;
    }

    public EntityAssert<T> hasIdAnnotation() {
        String failMessage = "\nExpecting class:\n  <%s>\nto have field annotated with:\n  <@Id>\nbut hasn't";
        for (final Field field : actual.getDeclaredFields()) {
            if (field.isAnnotationPresent(javax.persistence.Id.class)) {
                return this;
            }
        }

        failWithMessage(failMessage, actual.getName());
        return this;
    }

    public EntityAssert<T> conformsToEqualsContract() {
        try {
            EntityEqualsTester<T> entityEqualsTester = new EntityEqualsTester<>(actual);
            entityEqualsTester.verifyAll();
            entityEqualsTester = new EntityEqualsTester<>(actual);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        return this;
    }

}
