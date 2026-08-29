import getQuery from "../../helper/getQuery";

export const getTable = async (table_id : string) : Promise<any> => {
    const sql = `
        SELECT t.id, t.name, t.requeriments, t.description, t.permitted, t.startdate, CONCAT('UTC', t.timezone) as timezone, 
               t.status as table_status, DATE_FORMAT(t.duration, '%H:%i') as duration, t.table_type
            FROM Tables t
            WHERE t.id = ?
                AND t.status != 'Deleted'`;

    const params = [table_id];
    const response = await getQuery(sql, params);

    return response;
}