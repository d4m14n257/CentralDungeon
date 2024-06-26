import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';

import ModalBase from "../../general/ModalBase";

import { modal } from '@/styles/tables/modal';

export default function RequerimentsModalTable (props) {
    const {isOpen, handleCloseModal, handleOpenRequestModal, handleDownloadFiles, requeriments} = props

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            <Box sx={modal.body}>
                <Box sx={modal.header}>
                    <Typography variant="h5">
                        Requisitos
                    </Typography>
                </Box>
                <Box sx={modal.content}>
                    {requeriments ? 
                    <Typography variant="body2">
                        Lorem Ipsum es simplemente el texto de relleno de las imprentas y archivos de texto. Lorem Ipsum ha sido el texto de relleno estándar de las industrias desde el año 1500, 
                        cuando un impresor (N. del T. persona que se dedica a la imprenta) desconocido usó una galería de textos y los mezcló de tal manera que logró hacer un libro de textos especimen. 
                        No sólo sobrevivió 500 años, sino que tambien ingresó como texto de relleno en documentos electrónicos, quedando esencialmente igual al original. Fue popularizado en los 60s con 
                        la creación de las hojas "Letraset", las cuales contenian pasajes de Lorem Ipsum, y más recientemente con software de autoedición, como por ejemplo Aldus PageMaker, 
                        el cual incluye versiones de Lorem Ipsum.
                        Al contrario del pensamiento popular, el texto de Lorem Ipsum no es simplemente texto aleatorio. Tiene sus raices en una pieza clásica de la literatura del Latin, que data 
                        del año 45 antes de Cristo, haciendo que este adquiera mas de 2000 años de antiguedad. Richard McClintock, un profesor de Latin de la Universidad de Hampden-Sydney en Virginia, 
                        encontró una de las palabras más oscuras de la lengua del latín, "consecteur", en un pasaje de Lorem Ipsum, y al seguir leyendo distintos textos del latín, descubrió la fuente 
                        indudable. Lorem Ipsum viene de las secciones 1.10.32 y 1.10.33 de "de Finnibus Bonorum et Malorum" (Los Extremos del Bien y El Mal) por Cicero, escrito en el año 45 antes de Cristo. 
                        Este libro es un tratado de teoría de éticas, muy popular durante el Renacimiento. La primera linea del Lorem Ipsum, "Lorem ipsum dolor sit amet..", viene de una linea en la sección 1.10.32
                    </Typography> : 
                    <></> }
                </Box>
                <Box sx={modal.footer}>
                    <ButtonGroup>
                        <Button
                            onClick={() => {
                                handleCloseModal();
                                handleOpenRequestModal();
                        }}>
                            Solicitar
                        </Button>
                        <Button
                            onClick={handleDownloadFiles}
                        >  
                            Descargar
                        </Button>
                    </ButtonGroup>
                </Box>
            </Box>
        </ModalBase>
    );
}