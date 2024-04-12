import express, { Router } from "express";
import { createCatalogues, getCatalogueIndex, getCatalogues, setCatalogueIndex } from "../handlers/catalogues";
import { Systems } from "../models/models";

export const systems : Router = express.Router();
const table_name = 'Systems';
const column_name = 'system_id'

systems.get('/:name', getCatalogues(table_name));
systems.get('/index/:index', getCatalogueIndex(table_name));

systems.post('/', createCatalogues<Systems>(table_name));

systems.put('/index', setCatalogueIndex<Systems>(table_name, column_name));