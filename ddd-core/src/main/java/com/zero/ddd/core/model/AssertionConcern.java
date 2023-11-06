package com.zero.ddd.core.model;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import org.hibernate.validator.HibernateValidator;

import com.zero.helper.GU;

import lombok.val;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 21, 2020 4:42:22 PM
 * @Desc 些年若许,不负芳华.
 * 
 * 通用验证逻辑
 *
 */
public class AssertionConcern {
    
    public static AssertionConcern ASSERT = new AssertionConcern();

    protected AssertionConcern() {
        super();
    }

    protected static Validator validator = 
    		Validation.byProvider(
    				HibernateValidator.class)
    		.configure()
    		.failFast(false)
    		.buildValidatorFactory()
    		.getValidator();

    public void validate() {
        Set<ConstraintViolation<AssertionConcern>> constraintViolations = validator.validate(this);
        handleConstrantViolations(constraintViolations);
    }

    /**
     * 处理违规条项
     * 
     * @param validate
     */
    private <T> void handleConstrantViolations(Set<ConstraintViolation<T>> constraintViolations) {
        if (constraintViolations.size() > 0) {
            String errMsg = constraintViolations.stream().map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException(errMsg);
        }
    }

    public void validate(Object obj, String property) {
        val constraintViolations = validator.validateProperty(obj, property);
        handleConstrantViolations(constraintViolations);
    }

    public void validate(String property) {
        validate(this, property);
    }

    protected void assertArgumentEquals(Object anObject1, Object anObject2, String aMessage) {
        if (!anObject1.equals(anObject2)) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentFalse(boolean aBoolean, String aMessage) {
        if (aBoolean) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentLength(String aString, int aMaximum, String aMessage) {
        int length = aString.trim().length();
        if (length > aMaximum) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentLength(String aString, int aMinimum, int aMaximum, String aMessage) {
        int length = aString.trim().length();
        if (length < aMinimum || length > aMaximum) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentNotEmpty(String aString, String aMessage) {
        if (aString == null || aString.trim().isEmpty()) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentNotEquals(Object anObject1, Object anObject2, String aMessage) {
        if (anObject1.equals(anObject2)) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentNotNull(Object anObject, String aMessage) {
        if (anObject == null) {
            throw new IllegalArgumentException(aMessage);
        }
    }
    
    protected <E> void assertArgumentNotNullAndEmpty(Collection<E> anObject, String aMessage) {
        if (anObject == null || anObject.isEmpty()) {
            throw new IllegalArgumentException(aMessage);
        }
    }
    
    protected <E> void assertArgumentNotNullAndEmpty(String anObject, String aMessage) {
        if (anObject == null || anObject.isEmpty()) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentRange(double aValue, double aMinimum, double aMaximum, String aMessage) {
        if (aValue < aMinimum || aValue > aMaximum) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentRange(float aValue, float aMinimum, float aMaximum, String aMessage) {
        if (aValue < aMinimum || aValue > aMaximum) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentRange(int aValue, int aMinimum, int aMaximum, String aMessage) {
        if (aValue < aMinimum || aValue > aMaximum) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentRange(long aValue, long aMinimum, long aMaximum, String aMessage) {
        if (aValue < aMinimum || aValue > aMaximum) {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertArgumentTrue(boolean aBoolean, String aMessage, Object...args) {
        if (!aBoolean) {
            parseErrorMsg(aMessage, args);
        }
    }

    private void parseErrorMsg(String aMessage, Object... args) {
        if (GU.notNullAndEmpty(args)) {
            throw new IllegalArgumentException(
                    String.format(aMessage.replace("{}", "%s"), args));
        } else {
            throw new IllegalArgumentException(aMessage);
        }
    }

    protected void assertStateFalse(boolean aBoolean, String aMessage) {
        if (aBoolean) {
            throw new IllegalStateException(aMessage);
        }
    }

    protected void assertStateTrue(boolean aBoolean, String aMessage) {
        if (!aBoolean) {
            throw new IllegalStateException(aMessage);
        }
    }
    
    protected void assertArgumentMatchRegex(String aText, String aRegex, String aMessage) {
    	Matcher matcher = 
    			Pattern.compile(aRegex)
    			.matcher(aText);
    	if (!matcher.matches()) {
    		throw new IllegalStateException(aMessage);
    	}
    }
    
    public static void main(String[] args) {
        AssertionConcern assertionConcern = new AssertionConcern();
        assertionConcern.assertArgumentTrue(Math.random() < 0.0d, "数值必须小于%s", 0.0d);
    }

}