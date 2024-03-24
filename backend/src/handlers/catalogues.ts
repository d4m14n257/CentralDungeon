import { Request, Response } from "express";
import { Connection, ResultSetHeader } from "mysql2/promise";
import { v4 as uuidv4 } from 'uuid';
import { Platforms, Systems, Tags } from "../models/models";

export function createCatalogues<T extends Tags | Systems | Platforms>(conn : Connection, table_name : string) {
    const sql = `INSERT INTO ${table_name} (id, name) VALUES (?, ?)`;

    return async (req : Request, res : Response) => {
        try {
            await conn.beginTransaction
            const id = uuidv4();

            const body : T = { id: id, ...req.body };
            await conn.execute<ResultSetHeader>(sql, Object.values(body)).then(
                await conn.commit
            ).catch((err) => {                
                throw {http_status: 418, ...err}
            })
            
            res.status(200).send('Successfully')
        }
        catch(err : any) {
            await conn.rollback;
            res.status(err.http_status ? err.http_status : 409).send(err)
        }
    }
}

export function getCatalogues(conn : Connection, table_name : string) {
    const sql = `SELECT id, name FROM ${table_name} WHERE name LIKE ? AND status = 'Allowed'`;

    return async (req: Request, res : Response) => {
        try {
            const name : string = req.params.name;
            await conn.execute(sql, [`${name}%`]).then(([rows, fields]) => {
                res.status(200).send(rows);
            }).catch((err) => {
                throw {http_status: 503, ...err}
            })
            
        }
        catch(err : any) {
            res.status(err.http_status ? err.http_status : 500).send(err)
        }
    }
}

export function getCatalogueIndex(conn : Connection, table_name : string) {
    const sql = `SELECT id, name FROM ${table_name} WHERE id = ?`;

    return async(req: Request, res: Response) => {
        try {
            const index = req.params.index;
            await conn.execute(sql, [index]).then(([rows, field]) => {
                res.status(200).send(rows);
            }).catch((err) => {
                throw {http_status: 503, ...err}
            });
        }
        catch (err: any){
            res.status(err.http_status ? err.http_status : 500).send(err)
        }
    }
}

/*
    WITH RECURSIVE find_first AS (
    SELECT id, name, system_id
    FROM Systems
    WHERE id = '3123213123123dasdasgsdfhghjhfjgf4556'
    UNION ALL
    SELECT s.id, s.name, s.system_id
    FROM Systems s
    JOIN find_first f ON s.id = f.system_id
    )
    SELECT * FROM find_first WHERE system_id is NULL ;

*/

export function setCatalogueIndex<T extends Tags | Systems | Platforms>(conn : Connection, table_name : string, column_name : string) {
    const sql = `UPDATE ${table_name} SET ${column_name} = ? WHERE id = ?`;

    return async(req: Request, res: Response) => {
        try {
            await conn.beginTransaction
            const body : T = req.body;

            await conn.execute<ResultSetHeader>(sql, Object.values(body)).then(
                await conn.commit
            ).catch((err) => {
                throw {http_status: 418, ...err}
            });

            res.status(200).send("Succesfully")
        }
        catch (err: any) {
            await conn.rollback
            res.status(err.http_status ? err.http_status : 500).send(err)
        }
    }
}