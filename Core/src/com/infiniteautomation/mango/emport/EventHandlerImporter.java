package com.infiniteautomation.mango.emport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.infiniteautomation.mango.spring.service.EventHandlerService;
import com.infiniteautomation.mango.util.exception.NotFoundException;
import com.infiniteautomation.mango.util.exception.ValidationException;
import com.serotonin.json.JsonException;
import com.serotonin.json.type.JsonArray;
import com.serotonin.json.type.JsonObject;
import com.serotonin.json.type.JsonValue;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.i18n.TranslatableJsonException;
import com.serotonin.m2m2.module.EventHandlerDefinition;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.rt.event.type.EventType;
import com.serotonin.m2m2.vo.event.AbstractEventHandlerVO;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

public class EventHandlerImporter<EH extends AbstractEventHandlerVO<EH>> extends Importer {
    
    private final EventHandlerService<EH> service;
    
    public EventHandlerImporter(JsonObject json, EventHandlerService<EH> service, PermissionHolder user) {
        super(json, user);
        this.service = service;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void importImpl() {
        EH handler = null;
        String xid = json.getString("xid");
        if (StringUtils.isBlank(xid)) {
            xid = service.getDao().generateUniqueXid();
        }else {
            try {
                handler = service.get(xid, user);
            }catch(NotFoundException e) {
                //Nothing, done below
            }          
        }

        if (handler == null) {
            String typeStr = json.getString("handlerType");
            if (StringUtils.isBlank(typeStr))
                addFailureMessage("emport.eventHandler.missingType", xid, ModuleRegistry.getEventHandlerDefinitionTypes());
            else {
                EventHandlerDefinition<?> def = ModuleRegistry.getEventHandlerDefinition(typeStr);
                if (def == null)
                    addFailureMessage("emport.eventHandler.invalidType", xid, typeStr,
                            ModuleRegistry.getEventHandlerDefinitionTypes());
                else {
                    handler = (EH)def.baseCreateEventHandlerVO();
                    handler.setXid(xid);
                }
            }
        }else {
            //We want to only add event types via import so load existing in first
            handler.setEventTypes(service.getDao().getEventTypesForHandler(handler.getId()));
        }

        JsonObject et = json.getJsonObject("eventType");
        JsonArray ets = json.getJsonArray("eventTypes");
        
        if(handler != null) {
            try {
                ctx.getReader().readInto(handler, json);
                
                Set<EventType> eventTypes;
                if(handler.getEventTypes() == null) {
                    eventTypes = new HashSet<>();
                }else {
                    eventTypes = new HashSet<>(handler.getEventTypes());
                }
                // Find the event type.
                if(et != null)
                    eventTypes.add(ctx.getReader().read(EventType.class, et));
                else if(ets != null) {
                    Iterator<JsonValue> iter = ets.iterator();
                    while(iter.hasNext())
                        eventTypes.add(ctx.getReader().read(EventType.class, iter.next()));
                }
                
                if(eventTypes.size() > 0)
                    handler.setEventTypes(new ArrayList<>(eventTypes));
    
                boolean isnew = handler.getId() == Common.NEW_ID;
                if(isnew) {
                    service.insert(handler, user);
                }else {
                    service.update(handler.getId(), handler, user);
                }

            }catch(ValidationException e) {
                setValidationMessages(e.getValidationResult(), "emport.eventHandler.prefix", xid);
            }
            catch (TranslatableJsonException e) {
                addFailureMessage("emport.eventHandler.prefix", xid, e.getMsg());
            }
            catch (JsonException e) {
                addFailureMessage("emport.eventHandler.prefix", xid, getJsonExceptionMessage(e));
            }
        }
    }
}
