import express, { Router } from "express";
import { conn } from "../config/database";
import { getTablesIndex } from "../handlers/tables";

export const tables : Router = express.Router();

tables.get('/:user_id', getTablesIndex(conn));

