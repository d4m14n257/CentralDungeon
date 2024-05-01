import express, { Router } from "express";
import { handleCreateCatalogues, handleGetCatalogues } from "../handlers/catalogues";
import { Platforms } from "../models/models";

export const platforms : Router = express.Router();
const table_name = 'Platforms';

platforms.get('/:name', handleGetCatalogues(table_name))

platforms.post('/', handleCreateCatalogues<Platforms>(table_name));