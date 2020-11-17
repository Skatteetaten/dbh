-- Add this procedure to Oracle server to kill sessions during tests.
CREATE OR REPLACE PROCEDURE sys.kill_dev_session(p_sid NUMBER, p_serial NUMBER)
AS
    v_user VARCHAR2(30);
BEGIN
    SELECT MAX(username)
    INTO v_user
    FROM v$session
    WHERE sid = p_sid
      AND serial# = p_serial;

    IF v_user NOT IN ('SYS', 'SYSTEM', 'AOS_API_USER', 'RESIDENTS', 'ORACLE_EXPORTER', 'DBSNMP', 'DBDRIFT') THEN --the list can be extended
         EXECUTE IMMEDIATE 'ALTER SYSTEM KILL SESSION ''' || p_sid || ',' || p_serial || '''';
    ELSIF v_user IS NULL THEN
         RAISE_APPLICATION_ERROR(-20001,'Session has Expired or Invalid sid/serial Arguments Passed');
    ELSE
         RAISE_APPLICATION_ERROR(-20002,'Unauthorized Attempt to Kill a Non-Dev Session has been Blocked.');
    END IF;
END kill_dev_session;


-- Add:
-- GRANT EXECUTE ON kill_dev_session TO AOS_API_USER;
