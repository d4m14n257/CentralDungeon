import { useRef, useContext } from 'react';

import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';

import { Editor } from "@tinymce/tinymce-react";

import ModalBase from "../../general/ModalBase";

import { modal } from '@/styles/tables/modal';

import { ColorModeContext } from '@/contexts/ColorModeContext';
import UploadButton from '@/forms/UploadButton';

export default function RequestModalTable (props) {
    const {isOpen, handleCloseModal} = props;
    const { mode } = useContext(ColorModeContext)
    const editorRef = useRef(null);

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            <form>      
                <Box sx={modal.body}>
                        <Box sx={modal.header}>
                            <Typography variant="h5">
                                Solicitud
                            </Typography>
                        </Box>
                        <Box sx={modal.content}>
                            <Editor
                                apiKey={process.env.NEXT_PUBLIC_TINY_KEY}
                                onInit={(evt, editor) => (editorRef.current = editor)}
                                init={{
                                    skin: mode == 'dark' ? 'oxide-dark' : 'oxide',
                                    content_css: mode == 'dark' ? 'dark' : 'default',
                                    height: 400,
                                    menubar: false,
                                    toolbar:
                                        "undo redo | blocks | " +
                                        "bold italic forecolor | alignleft aligncenter " +
                                        "alignright alignjustify | bullist numlist outdent indent | " +
                                        "removeformat",
                                    content_style:
                                        "body { font-family:Helvetica,Arial,sans-serif; font-size:14px }",
                                    }}
                            />
                            <UploadButton />
                        </Box>
                        <Box sx={modal.footer}>
                            <Button variant='outlined' onClick={handleCloseModal}>Subir</Button>
                        </Box>
                </Box>
            </form>
        </ModalBase>
    );
}