import { PoolConnection } from "mysql2/promise";
import { Schedule } from "../../models/models";
import setQuery from "../../helper/setQuery";
import generateBase64 from "../../helper/generateBase64";

export const setScheduleTables = async (table_id : string, schedule : Schedule, query : PoolConnection) => {
    const id : string = await generateBase64()
    .then((data) => data[0][0].generated_id);

    const sql = `
        INSERT INTO Days (id, table_id, weekday, hourtime) VALUES (?, ?, ?, ?)`;

    const params = [id, table_id, schedule.weekday, schedule.hourtime];
    const response = await setQuery(sql, params, query);

    return await response;
}