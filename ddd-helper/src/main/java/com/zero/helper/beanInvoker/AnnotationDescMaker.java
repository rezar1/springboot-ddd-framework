
/**
 * Baijiahulian.com Inc. Copyright (c) 2014-2016 All Rights Reserved.
 */

package com.zero.helper.beanInvoker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.Data;

/**
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Oct 12, 2016
 * @Desc 创建类的annotation申明
 */
public class AnnotationDescMaker {

    public static final String annotation_format = "%s<%s>;";

    public static String makeAnnotatinDesc(AnnotationWrapper aw) {
        return makeAnnotationDesc(Arrays.asList(aw));
    }

    public static String makeAnnotationDesc(List<AnnotationWrapper> AnnotationWrappers) {
        StringBuilder sb = new StringBuilder("Ljava/lang/Object;");
        for (AnnotationWrapper wrapper : AnnotationWrappers) {
            sb.append(wrapper.getAnnotationDesc());
        }
        return sb.toString();
    }

    @Data
    public static class AnnotationWrapper {
        private String superClassDesc;
        private List<String> annotationParamDescs;

        public AnnotationWrapper(String superClassDesc, String...annotationParamDesc) {
            this.superClassDesc = superClassDesc.substring(0, superClassDesc.length() - 1);
            this.annotationParamDescs = new ArrayList<>();
            for (String aDesc : annotationParamDesc) {
                this.annotationParamDescs.add(aDesc);
            }
        }

        public String getAnnotationDesc() {
            StringBuilder sb = new StringBuilder();
            for (String desc : this.annotationParamDescs) {
                sb.append(desc);
            }
            return String.format(annotation_format, this.superClassDesc, sb.toString());
        }

    }

}
