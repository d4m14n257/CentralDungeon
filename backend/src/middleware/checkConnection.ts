import { NextFunction, Request, Response } from "express";
import { conn } from "../config/database";

export async function checkConnection (req: Request, res: Response, next : NextFunction) {
    try {
        await conn.getConnection().then((response) => {
            next();
        })
        .catch((err) => {
            throw err
        })
    }
    catch (err) {
        const message = "Connection with db could not be established"
        console.log(message)

        res.status(500).send({err})
    }
}