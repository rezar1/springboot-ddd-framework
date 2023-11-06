package com.zero.ddd.core.helper;

import java.util.ArrayList;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-07 05:08:00
 * @Desc 些年若许,不负芳华.
 *
 */
public class FixedSizeArrayList<E> extends ArrayList<E> {
	
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final long mMaxSize;
 
    public FixedSizeArrayList(int maxSize) {
        this.mMaxSize = maxSize;
    }
 
    @Override
    public boolean add(E o) {
        if (size() == mMaxSize) {//大于最大长度时，移除第一个
            remove(0);
        }
        return super.add(o);
    }
 
}

