import express, { Router } from "express";
import { getFirstClassTables, getGeneralView, getMasterView, getPublicTables } from "../handlers/tables";

export const tables : Router = express.Router();

tables.get('/player/:user_id', getGeneralView());
tables.get('/master/:user_id', getMasterView());
tables.get('/public-tables/:user_id', getPublicTables());
tables.get('/first-class-tables/:user_id', getFirstClassTables());

