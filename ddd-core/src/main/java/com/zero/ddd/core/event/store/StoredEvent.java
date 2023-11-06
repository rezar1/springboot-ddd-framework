//   Copyright 2012,2013 Vaughn Vernon
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package com.zero.ddd.core.event.store;

import java.time.LocalDateTime;

import com.zero.ddd.core.model.AssertionConcern;
import com.zero.helper.JacksonUtil;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper=false)
public class StoredEvent extends AssertionConcern {

    private String eventId;
    private String typeName;
    private String eventBody;
    private LocalDateTime eventTime;

    public <T> T toDomainEvent(Class<T> msgType) {
        T domainEvent = 
        		JacksonUtil.str2ObjNoError(
        				eventBody, 
        				msgType);
        if (domainEvent instanceof StoredEventIdAware) {
        	((StoredEventIdAware) domainEvent).setEventId(eventId);
        }
        return domainEvent;
    }

    public String typeName() {
        return this.typeName;
    }

}