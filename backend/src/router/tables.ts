import express, { Router } from "express";
import { createTable, getFirstClassTables, getGeneralView, getJoinedTables, getMasterView, getPublicTables, getTablesMasterList } from "../handlers/tables";

export const tables : Router = express.Router();

tables.get('/player/:user_id', getGeneralView());
tables.get('/master/:user_id', getMasterView());
tables.get('/public-tables/:user_id', getPublicTables());
tables.get('/first-class-tables/:user_id', getFirstClassTables());
tables.get('/joined-tables/:user_id', getJoinedTables());
tables.get('/master/list/:user_id', getTablesMasterList());

tables.post('/master', createTable());

