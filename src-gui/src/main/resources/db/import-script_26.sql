INSERT INTO VERSION_ENTITY (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (26, 26, CURRENT_TIMESTAMP);

INSERT  INTO ACTIVITY_TYPE (activity_type_id, activity_name) VALUES 
	(42, 'UPDATE_SW_PORT_PROPERTIES');

INSERT INTO KILDA_PERMISSION (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(355, 'sw_update_port_properties', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission to update switch port properties');
	
INSERT INTO ROLE_PERMISSION (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 355);