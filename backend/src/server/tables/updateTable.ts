import { PoolConnection } from "mysql2/promise";
import { Table } from "../../models/models";
import setQuery from "../../helper/setQuery";

export const updateTable = async (table : Table, query : PoolConnection) => {
    let sql = `
        UPDATE Tables
            SET `;

    const params = [];

    if(table.name) {
        sql += `name = ?,`;
        params.push(table.name);
    }

    if(table.description) {
        sql += `description = ?,`;
        params.push(table.description);
    }

    if(table.permitted) {
        sql += `permitted = ?,`;
        params.push(table.permitted);
    }

    if(table.startdate) {
        sql += `startdate = ?,`;
        params.push(table.startdate);
    }

    if(table.timezone) {
        sql += `timezone = ?,`;
        params.push(table.timezone);
    }

    if(table.duration) {
        sql += `duration = ?,`;
        params.push(table.duration);
    }

    sql = sql.slice(0, -1);

    const response = await setQuery(sql, params, query);

    return {...response};
}