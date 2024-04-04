import mysql, { Connection, ConnectionOptions, Pool } from 'mysql2/promise';
import dotenv from 'dotenv';

dotenv.config();

const access: ConnectionOptions  = {
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    host: process.env.DB_HOST,
    database: process.env.DB_NAME,
    port: Number(process.env.DB_PORT),
    connectionLimit: 20
}

export const conn: Pool = mysql.createPool(access);