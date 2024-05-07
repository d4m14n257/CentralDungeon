import { PoolConnection } from "mysql2/promise";
import { Schedule } from "../../models/models";
import setQuery from "../../helper/setQuery";

export const setScheduleTables = async (table_id : string, schedule : Schedule, query : PoolConnection) => {
    const sql = `
        INSERT INTO Days (table_id, weekday, hourtime) VALUES (?, ?, ?)`;

    const params = [table_id, schedule.weekday, schedule.hourtime];
    const response = await setQuery(sql, params, query);

    return await response;
}