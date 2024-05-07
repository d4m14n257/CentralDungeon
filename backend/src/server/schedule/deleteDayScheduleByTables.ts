import setQuery from "../../helper/setQuery";
import { PoolConnection } from "mysql2/promise";
import { Schedule } from "../../models/models";

export const deleteDayScheduleByTables = async (table_id : string, schedule: Schedule, query : PoolConnection) => {
    const sql = `
        UPDATE Days
            SET status = 'Deleted'
            WHERE table_id = ?
                AND weekday = ?
                AND hourtime = ?`;

    const params = [table_id, schedule.weekday, schedule.hourtime];
    const response = await setQuery(sql, params, query);

    return response;
}