import express, { Router } from "express";
import { handleCreateCatalogues, handleGetCatalogues, handleSetCatalogue } from "../handlers/catalogues";
import { Platforms } from "../models/models";

export const platforms : Router = express.Router();
const table_name = 'Platforms';

platforms.get('/:name', handleGetCatalogues(table_name))

platforms.post('/', handleCreateCatalogues<Platforms>(table_name));

platforms.put('/tables/:table_id', handleSetCatalogue<Platforms>(table_name));