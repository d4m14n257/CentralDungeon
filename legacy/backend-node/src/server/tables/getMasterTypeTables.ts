import getQuery from "../../helper/getQuery";

export const getMasterTypeTables = async (utc : Promise<string>, user_id : string, type : string) : Promise<any> => {
    const sql = `
        SELECT t.id, t.name, t.description, pt.players, CONVERT_TZ(t.startdate, t.timezone, ?) as startdate, t.status
            FROM Tables t 
                JOIN Masters m ON m.table_id = t.id
                JOIN Players_Table pt ON pt.table_id = t.id
            WHERE m.user_master_id = ?
                AND m.master_type = '${type}'
                AND t.status != 'Deleted'`;

        const params = [utc, user_id];
        return await getQuery(sql, params);
}