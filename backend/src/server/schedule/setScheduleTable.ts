import { PoolConnection } from "mysql2/promise";
import { Schedule } from "../../models/models";
import setQuery from "../../helper/setQuery";

export const setScheduleTables = async (table_id : string, schedule : Schedule, query : PoolConnection) => {
    const sql = `
        INSERT INTO Days (table_id, weekday, hourtime) VALUES (?, ?, ?)`;

    const params = [table_id, schedule.weekday, schedule.hourtime];
    const response = await setQuery(sql, params, query);

    if(response.errno == 1062) {
        const sql_update = `
            UPDATE Days
                SET status = 'Created'
            WHERE table_id = ?
                AND weekday = ?
                AND hourtime = ?`;

        const updated = await setQuery(sql_update, params, query);
        return updated;
    }
    else
        return await response;
}