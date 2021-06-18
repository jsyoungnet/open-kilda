INSERT INTO VERSION_ENTITY (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (18, 18, CURRENT_TIMESTAMP);
	
INSERT  INTO ACTIVITY_TYPE (activity_type_id, activity_name) VALUES 
	(39, 'UPDATE_ISL_BANDWIDTH');

INSERT INTO KILDA_PERMISSION (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(347, 'isl_update_bandwidth', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission to update isl bandwidth');
	
INSERT INTO ROLE_PERMISSION (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 347);